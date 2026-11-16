package com.homeia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.domain.AlertSeverity;
import com.homeia.domain.AlertType;
import com.homeia.domain.DeviceProtocol;
import com.homeia.domain.DeviceProvider;
import com.homeia.domain.EdgeStatus;
import com.homeia.edge.EdgeRegistryService;
import com.homeia.persistence.EdgeNodeEntity;
import com.homeia.security.AuthPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inventario temporal de candidatos que reportan los HomeIA Edge de cada casa. Descubrir no implica
 * emparejar ni dar acceso: un administrador debe incorporar cada candidato.
 */
@Slf4j
@Service
public class DeviceDiscoveryService {

    public record Candidate(String id, UUID edgeId, String edgeName, String name, String provider, String protocol,
                            String address, String manufacturer, String model, String service,
                            List<String> capabilities, Instant discoveredAt) {
    }

    private static final int MAX_CANDIDATES_PER_HOME = 500;

    private final EdgeRegistryService edges;
    private final AuditService audit;
    private final AlertService alerts;
    private final Map<UUID, Map<String, Candidate>> candidatesByHome = new ConcurrentHashMap<>();

    public DeviceDiscoveryService(EdgeRegistryService edges, AuditService audit, AlertService alerts) {
        this.edges = edges;
        this.audit = audit;
        this.alerts = alerts;
    }

    /** Pide a todos los Edge en línea de la casa que busquen dispositivos en su LAN. */
    @Transactional(readOnly = true)
    public Map<String, Object> scan(AuthPrincipal principal) {
        if (!edges.mqttReady()) {
            return Map.of("accepted", false, "message", "El backend no está conectado al broker MQTT; revisa MQTT_ENABLED y MQTT_BROKER_URL");
        }
        List<EdgeNodeEntity> online = edges.list(principal.homeId()).stream()
                .filter(e -> e.getStatus() == EdgeStatus.ONLINE).toList();
        if (online.isEmpty()) {
            return Map.of("accepted", false, "message", "No hay ningún HomeIA Edge en línea en esta casa. Enrola uno y comprueba su heartbeat.");
        }
        int sent = 0;
        for (EdgeNodeEntity edge : online) {
            try {
                edges.send(edge, "scan", Map.of("requestedAt", Instant.now().toString()));
                sent++;
            } catch (RuntimeException e) {
                log.warn("No se pudo pedir la búsqueda al Edge {}: {}", edge.getName(), e.getMessage());
            }
        }
        audit.record(principal, "DISCOVERY_SCAN", "EDGE", null, sent + " Edge(s)");
        return Map.of("accepted", sent > 0, "message",
                "Búsqueda solicitada a " + sent + " HomeIA Edge. Los candidatos aparecerán en unos segundos.");
    }

    /**
     * Abre (seconds > 0) o cierra (0) el emparejamiento Zigbee en los Edge de la casa con Zigbee activo.
     * Siempre con límite de tiempo; EdgeInboundService verifica después que el coordinador lo cerró.
     */
    @Transactional
    public Map<String, Object> permitZigbeeJoin(AuthPrincipal principal, UUID edgeId, int seconds) {
        if (seconds != 0) {
            try {
                PermitJoinPolicy.validateSeconds(seconds);
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest(e.getMessage());
            }
        }
        if (!edges.mqttReady()) throw ApiException.unavailable("El backend no está conectado al broker MQTT");
        List<EdgeNodeEntity> targets = (edgeId == null ? edges.list(principal.homeId())
                : List.of(edges.get(principal.homeId(), edgeId))).stream()
                .filter(e -> e.getStatus() == EdgeStatus.ONLINE)
                .filter(e -> edgeId != null || e.getZigbeeBridgeState() != null)
                .toList();
        if (targets.isEmpty()) {
            throw ApiException.unavailable("No hay un HomeIA Edge en línea con Zigbee2MQTT activo en esta casa");
        }
        Instant until = seconds == 0 ? null : Instant.now().plusSeconds(seconds);
        for (EdgeNodeEntity edge : targets) {
            edge.setZigbeePermitJoinUntil(until);
            edge.setUpdatedAt(Instant.now());
            edges.send(edge, "permit_join", Map.of("seconds", seconds));
            audit.record(principal, seconds == 0 ? "ZIGBEE_PERMIT_JOIN_CLOSED" : "ZIGBEE_PERMIT_JOIN_OPENED",
                    "EDGE", edge.getId().toString(), seconds == 0 ? edge.getName() : edge.getName() + " · " + seconds + " s");
            if (seconds > 0) {
                alerts.raiseOnce(principal.homeId(), null, AlertType.ZIGBEE_PAIRING, AlertSeverity.INFO,
                        "Emparejamiento Zigbee abierto",
                        principal.email() + " abrió el emparejamiento en " + edge.getName() + " durante " + seconds
                                + " s. Cualquier dispositivo Zigbee cercano puede unirse mientras esté abierto.", null);
            }
        }
        return Map.of("accepted", true, "seconds", seconds, "edges", targets.size());
    }

    @Transactional(readOnly = true)
    public List<Candidate> list(UUID homeId) {
        Map<String, Candidate> home = candidatesByHome.get(homeId);
        if (home == null) return List.of();
        Instant cutoff = Instant.now().minusSeconds(600);
        home.values().removeIf(c -> c.discoveredAt().isBefore(cutoff));
        return home.values().stream().sorted(Comparator.comparing(Candidate::discoveredAt).reversed()).toList();
    }

    public Optional<Candidate> get(UUID homeId, String id) {
        return Optional.ofNullable(candidatesByHome.getOrDefault(homeId, Map.of()).get(id));
    }

    public void forget(UUID homeId, String id) {
        Map<String, Candidate> home = candidatesByHome.get(homeId);
        if (home != null) home.remove(id);
    }

    /** Resultado de búsqueda firmado por un Edge: {"scanId", "candidates":[…]} */
    public int ingest(EdgeNodeEntity edge, JsonNode body) {
        JsonNode list = body.isArray() ? body : body.path("candidates");
        if (!list.isArray()) return 0;
        Map<String, Candidate> home = candidatesByHome.computeIfAbsent(edge.getHomeId(), k -> new ConcurrentHashMap<>());
        int accepted = 0;
        for (JsonNode n : list) {
            if (home.size() >= MAX_CANDIDATES_PER_HOME) break;
            String address = text(n, "address");
            String provider = upper(text(n, "provider"));
            String protocol = upper(text(n, "protocol"));
            if (address == null || provider == null || protocol == null) continue;
            DeviceProvider p;
            try {
                p = DeviceProvider.valueOf(provider);
                DeviceProtocol.valueOf(protocol);
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            Optional<String> addressProblem = p == DeviceProvider.ZIGBEE
                    ? NetworkAddressPolicy.checkZigbeeName(address)
                    : NetworkAddressPolicy.checkPrivateIpv4(address);
            if (addressProblem.isPresent()) continue;
            String identity = edge.getId() + "|" + provider + "|" + address;
            String id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
            List<String> capabilities = new ArrayList<>();
            JsonNode caps = n.path("capabilities");
            if (caps.isArray()) caps.forEach(c -> {
                if (c.isTextual() && capabilities.size() < 32) capabilities.add(c.asText());
            });
            String name = Optional.ofNullable(text(n, "name")).orElse(address);
            home.put(id, new Candidate(id, edge.getId(), edge.getName(), limit(name, 120), provider, protocol, address,
                    limit(text(n, "manufacturer"), 100), limit(text(n, "model"), 120), limit(text(n, "service"), 200),
                    List.copyOf(capabilities), Instant.now()));
            accepted++;
        }
        return accepted;
    }

    public DeviceProvider provider(Candidate c) {
        return DeviceProvider.valueOf(c.provider());
    }

    public DeviceProtocol protocol(Candidate c) {
        return DeviceProtocol.valueOf(c.protocol());
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText().trim();
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
