package com.homeia.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.config.HomeIaProperties;
import com.homeia.domain.EdgeStatus;
import com.homeia.integration.Json;
import com.homeia.integration.MqttGateway;
import com.homeia.persistence.EdgeNodeEntity;
import com.homeia.persistence.EdgeNodeRepository;
import com.homeia.security.AuthPrincipal;
import com.homeia.security.SecretCipher;
import com.homeia.service.ApiException;
import com.homeia.service.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enrolamiento, rotación y revocación de HomeIA Edge, más el canal firmado hacia cada Edge.
 * No depende de los servicios de dispositivos (los mensajes entrantes los procesa EdgeInboundService).
 */
@Slf4j
@Service
public class EdgeRegistryService {

    /** Tiempo durante el que el secreto anterior sigue valiendo tras una rotación, para actualizar el Edge. */
    public static final Duration ROTATION_GRACE = Duration.ofMinutes(15);

    public record Enrollment(EdgeNodeEntity edge, String secret, Map<String, String> env) {
    }

    /** Mensaje entrante ya autenticado. */
    public record VerifiedMessage(EdgeNodeEntity edge, String kind, JsonNode body) {
    }

    private final EdgeNodeRepository repository;
    private final SecretCipher cipher;
    private final MqttGateway mqtt;
    private final AuditService audit;
    private final HomeIaProperties.Edge config;
    private final ReplayGuard replayGuard;
    /** Edge que aún firman con el secreto anterior (rotación en curso): se les responde con ese mismo secreto. */
    private final java.util.Set<UUID> usingPreviousSecret = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public EdgeRegistryService(EdgeNodeRepository repository, SecretCipher cipher, MqttGateway mqtt,
                               AuditService audit, HomeIaProperties properties) {
        this.repository = repository;
        this.cipher = cipher;
        this.mqtt = mqtt;
        this.audit = audit;
        this.config = properties.edge() == null ? new HomeIaProperties.Edge(null, 0, 0) : properties.edge();
        this.replayGuard = new ReplayGuard(Duration.ofSeconds(this.config.replayWindow()));
    }

    public HomeIaProperties.Edge config() {
        return config;
    }

    // ---------- Administración ----------

    @Transactional(readOnly = true)
    public List<EdgeNodeEntity> list(UUID homeId) {
        return repository.findByHomeIdOrderByCreatedAtAsc(homeId);
    }

    @Transactional(readOnly = true)
    public EdgeNodeEntity get(UUID homeId, UUID edgeId) {
        return repository.findByIdAndHomeId(edgeId, homeId).orElseThrow(() -> ApiException.notFound("Edge"));
    }

    @Transactional
    public Enrollment enroll(AuthPrincipal principal, String name) {
        if (name == null || name.isBlank() || name.length() > 120) {
            throw ApiException.badRequest("El nombre del Edge es obligatorio (máx. 120 caracteres)");
        }
        String secret = EdgeSigner.newSecret();
        EdgeNodeEntity edge = new EdgeNodeEntity();
        edge.setHomeId(principal.homeId());
        edge.setName(name.trim());
        edge.setSecretCiphertext(cipher.encrypt(secret));
        repository.save(edge);
        audit.record(principal, "EDGE_ENROLLED", "EDGE", edge.getId().toString(), edge.getName());
        return new Enrollment(edge, secret, env(edge, secret));
    }

    /** Nuevo secreto; el anterior sigue valiendo ROTATION_GRACE para que el Edge se actualice sin cortes. */
    @Transactional
    public Enrollment rotate(AuthPrincipal principal, UUID edgeId) {
        EdgeNodeEntity edge = get(principal.homeId(), edgeId);
        if (edge.getStatus() == EdgeStatus.REVOKED) {
            throw ApiException.conflict("El Edge está revocado; enrola uno nuevo");
        }
        String secret = EdgeSigner.newSecret();
        Instant now = Instant.now();
        edge.setPreviousSecretCiphertext(edge.getSecretCiphertext());
        edge.setPreviousValidUntil(now.plus(ROTATION_GRACE));
        edge.setSecretCiphertext(cipher.encrypt(secret));
        edge.setRotatedAt(now);
        edge.setUpdatedAt(now);
        audit.record(principal, "EDGE_SECRET_ROTATED", "EDGE", edgeId.toString(), edge.getName());
        return new Enrollment(edge, secret, env(edge, secret));
    }

    @Transactional
    public EdgeNodeEntity revoke(AuthPrincipal principal, UUID edgeId) {
        EdgeNodeEntity edge = get(principal.homeId(), edgeId);
        Instant now = Instant.now();
        edge.setStatus(EdgeStatus.REVOKED);
        edge.setRevokedAt(now);
        edge.setPreviousSecretCiphertext(null);
        edge.setPreviousValidUntil(null);
        // El secreto se reemplaza por uno que nadie conoce: aunque alguien lo hubiera copiado, ya no sirve.
        edge.setSecretCiphertext(cipher.encrypt(EdgeSigner.newSecret()));
        edge.setUpdatedAt(now);
        audit.record(principal, "EDGE_REVOKED", "EDGE", edgeId.toString(), edge.getName());
        return edge;
    }

    @Transactional
    public void delete(AuthPrincipal principal, UUID edgeId) {
        EdgeNodeEntity edge = get(principal.homeId(), edgeId);
        repository.delete(edge);
        audit.record(principal, "EDGE_DELETED", "EDGE", edgeId.toString(), edge.getName());
    }

    private Map<String, String> env(EdgeNodeEntity edge, String secret) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("HOMEIA_EDGE_ID", edge.getId().toString());
        env.put("HOMEIA_EDGE_SECRET", secret);
        env.put("HOMEIA_EDGE_TOPIC_ROOT", config.root());
        return env;
    }

    // ---------- Canal firmado ----------

    /** Publica un mensaje firmado hacia el Edge. Lanza IllegalStateException si MQTT no está disponible. */
    public void send(EdgeNodeEntity edge, String kind, Object body) {
        if (edge.getStatus() == EdgeStatus.REVOKED) {
            throw new IllegalStateException("El Edge " + edge.getName() + " está revocado");
        }
        String secret = outboundSecret(edge);
        String edgeId = edge.getId().toString();
        String bodyJson = Json.write(body);
        long ts = Instant.now().getEpochSecond();
        String nonce = EdgeSigner.newNonce();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("v", 1);
        envelope.put("edgeId", edgeId);
        envelope.put("kind", kind);
        envelope.put("ts", ts);
        envelope.put("nonce", nonce);
        envelope.put("body", bodyJson);
        envelope.put("sig", EdgeSigner.sign(secret, edgeId, kind, ts, nonce, bodyJson));
        mqtt.publish(config.root() + "/" + edgeId + "/" + kind, Json.write(envelope));
    }

    /** Cifra un dato (credenciales) para que solo ese Edge pueda leerlo. */
    public String encryptFor(EdgeNodeEntity edge, String plaintext) {
        return EdgeSigner.encryptFor(outboundSecret(edge), edge.getId().toString(), plaintext);
    }

    /** Secreto con el que el Edge puede verificar hoy: el anterior si todavía no se actualizó tras rotar. */
    private String outboundSecret(EdgeNodeEntity edge) {
        boolean previousValid = edge.getPreviousSecretCiphertext() != null && edge.getPreviousValidUntil() != null
                && Instant.now().isBefore(edge.getPreviousValidUntil());
        if (previousValid && usingPreviousSecret.contains(edge.getId())) {
            return cipher.decrypt(edge.getPreviousSecretCiphertext());
        }
        return cipher.decrypt(edge.getSecretCiphertext());
    }

    public boolean mqttReady() {
        return mqtt.enabled() && mqtt.connected();
    }

    /**
     * Verifica un mensaje entrante: Edge existente y no revocado, tópico coherente con el sobre, firma válida
     * (secreto actual o anterior dentro de la gracia) y sin repetición. Vacío si debe ignorarse.
     */
    @Transactional(readOnly = true)
    public Optional<VerifiedMessage> verify(String topicEdgeId, String topicKind, String payload) {
        UUID edgeId;
        try {
            edgeId = UUID.fromString(topicEdgeId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        JsonNode envelope;
        try {
            envelope = Json.read(payload);
        } catch (IllegalArgumentException e) {
            log.warn("Mensaje de Edge {} con JSON inválido", topicEdgeId);
            return Optional.empty();
        }
        String kind = envelope.path("kind").asText("");
        String nonce = envelope.path("nonce").asText("");
        String body = envelope.path("body").asText(null);
        String sig = envelope.path("sig").asText(null);
        long ts = envelope.path("ts").asLong(0);
        if (!topicKind.equals(kind) || !topicEdgeId.equals(envelope.path("edgeId").asText("")) || body == null) {
            log.warn("Mensaje de Edge {} con sobre incoherente (tópico {}, tipo {})", topicEdgeId, topicKind, kind);
            return Optional.empty();
        }
        EdgeNodeEntity edge = repository.findById(edgeId).orElse(null);
        if (edge == null) {
            log.warn("Mensaje de un Edge no enrolado: {}", topicEdgeId);
            return Optional.empty();
        }
        if (edge.getStatus() == EdgeStatus.REVOKED) {
            log.warn("Mensaje de un Edge revocado: {} ({})", edge.getName(), topicEdgeId);
            return Optional.empty();
        }
        boolean valid = false;
        List<String> secrets = secrets(edge);
        for (int i = 0; i < secrets.size(); i++) {
            if (EdgeSigner.verify(secrets.get(i), topicEdgeId, kind, ts, nonce, body, sig)) {
                valid = true;
                if (i == 0) usingPreviousSecret.remove(edgeId);
                else usingPreviousSecret.add(edgeId);
                break;
            }
        }
        if (!valid) {
            log.warn("Firma inválida en mensaje {} del Edge {}", kind, edge.getName());
            return Optional.of(new VerifiedMessage(edge, "__invalid_signature", null));
        }
        if (!replayGuard.accept(topicEdgeId, ts, nonce)) {
            log.warn("Mensaje {} del Edge {} repetido o fuera de la ventana de tiempo (revisa el reloj/NTP del Edge)",
                    kind, edge.getName());
            return Optional.empty();
        }
        try {
            return Optional.of(new VerifiedMessage(edge, kind, Json.read(body)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private List<String> secrets(EdgeNodeEntity edge) {
        List<String> secrets = new ArrayList<>(2);
        secrets.add(cipher.decrypt(edge.getSecretCiphertext()));
        if (edge.getPreviousSecretCiphertext() != null && edge.getPreviousValidUntil() != null
                && Instant.now().isBefore(edge.getPreviousValidUntil())) {
            secrets.add(cipher.decrypt(edge.getPreviousSecretCiphertext()));
        }
        return secrets;
    }
}
