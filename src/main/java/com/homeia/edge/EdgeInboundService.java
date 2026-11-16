package com.homeia.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.domain.AlertSeverity;
import com.homeia.domain.AlertType;
import com.homeia.domain.DeviceEventType;
import com.homeia.domain.EdgeStatus;
import com.homeia.integration.EdgeMessageEvent;
import com.homeia.integration.Json;
import com.homeia.integration.MqttConnectedEvent;
import com.homeia.persistence.DeviceEntity;
import com.homeia.persistence.EdgeNodeEntity;
import com.homeia.persistence.EdgeNodeRepository;
import com.homeia.service.AlertService;
import com.homeia.service.AuditService;
import com.homeia.service.DeviceCredentialService;
import com.homeia.service.DeviceDiscoveryService;
import com.homeia.service.DeviceEventService;
import com.homeia.service.DeviceService;
import com.homeia.service.PermitJoinPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Procesa los mensajes firmados que publican los HomeIA Edge y vigila su salud:
 * hello → inventario (sync) · heartbeat → estado/métricas · discovery → candidatos · ack → confirmación de
 * órdenes · state → estado de dispositivos · event → eventos Zigbee y errores.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EdgeInboundService {

    /** Tipos que publica el Edge. Los demás (command, sync, scan…) son los que publica el backend: se ignoran. */
    private static final java.util.Set<String> INBOUND = java.util.Set.of("hello", "heartbeat", "discovery", "ack", "state", "event");
    private static final Duration SECURITY_ALERT_COOLDOWN = Duration.ofMinutes(15);

    private final EdgeRegistryService registry;
    private final EdgeNodeRepository edgeRepository;
    private final DeviceService deviceService;
    private final DeviceCommandService commandService;
    private final DeviceDiscoveryService discovery;
    private final DeviceCredentialService credentials;
    private final DeviceEventService deviceEvents;
    private final AlertService alerts;
    private final AuditService audit;
    private final Map<UUID, Instant> lastSecurityAlert = new ConcurrentHashMap<>();

    @EventListener
    @Transactional
    public void onMessage(EdgeMessageEvent event) {
        if (!INBOUND.contains(event.kind())) return;
        registry.verify(event.edgeId(), event.kind(), event.payload()).ifPresent(message -> {
            EdgeNodeEntity edge = edgeRepository.findById(message.edge().getId()).orElse(null);
            if (edge == null) return;
            if ("__invalid_signature".equals(message.kind())) {
                securityAlert(edge, "Se recibió un mensaje con firma inválida a nombre de " + edge.getName()
                        + ". Si rotaste el secreto, actualiza HOMEIA_EDGE_SECRET en el Edge; si no, alguien intenta suplantarlo.");
                return;
            }
            try {
                dispatch(edge, message.kind(), message.body());
            } catch (RuntimeException e) {
                log.warn("Mensaje {} del Edge {} no procesado: {}", message.kind(), edge.getName(), e.getMessage());
            }
        });
    }

    private void dispatch(EdgeNodeEntity edge, String kind, JsonNode body) {
        Instant now = Instant.now();
        switch (kind) {
            case "hello" -> {
                edge.setLastHelloAt(now);
                edge.setVersion(limit(body.path("version").asText(null), 40));
                edge.setPlatform(limit(body.path("platform").asText(null), 120));
                markOnline(edge, now);
                sync(edge);
            }
            case "heartbeat" -> heartbeat(edge, body, now);
            case "discovery" -> {
                markOnline(edge, now);
                int accepted = discovery.ingest(edge, body);
                log.debug("Edge {} reportó {} candidatos", edge.getName(), accepted);
            }
            case "ack" -> {
                markOnline(edge, now);
                commandService.onAck(edge, body).ifPresent(ack -> {
                    if (ack.state() != null) {
                        deviceService.applyEdgeReport(edge, ack.command().getDeviceId(), ack.state(),
                                "ZIGBEE".equals(body.path("provider").asText()));
                    }
                });
            }
            case "state" -> {
                markOnline(edge, now);
                UUID deviceId = uuid(body.path("deviceId").asText(""));
                boolean zigbee = "ZIGBEE".equals(body.path("provider").asText());
                deviceService.applyEdgeReport(edge, deviceId, body.path("state"), zigbee);
            }
            case "event" -> edgeEvent(edge, body);
            default -> { }
        }
        edge.setUpdatedAt(now);
    }

    private void heartbeat(EdgeNodeEntity edge, JsonNode body, Instant now) {
        edge.setLastHeartbeatAt(now);
        if (body.hasNonNull("version")) edge.setVersion(limit(body.get("version").asText(), 40));
        edge.setQueueDepth(Math.max(0, body.path("queueDepth").asInt(0)));
        JsonNode zigbee = body.path("zigbee");
        if (zigbee.isObject()) {
            edge.setZigbeeBridgeState(limit(zigbee.path("bridgeState").asText(null), 16));
            edge.setZigbeePermitJoin(zigbee.path("permitJoin").asBoolean(false));
        } else {
            edge.setZigbeeBridgeState(null);
            edge.setZigbeePermitJoin(false);
        }
        JsonNode metrics = body.path("metrics");
        if (metrics.isObject()) {
            String json = Json.write(metrics);
            edge.setMetricsJson(json.length() > 4000 ? "{}" : json);
        }
        markOnline(edge, now);
        enforcePermitJoin(edge, now);
    }

    private void edgeEvent(EdgeNodeEntity edge, JsonNode body) {
        String type = body.path("type").asText("");
        String details = limit(body.path("details").asText(""), 900);
        switch (type) {
            case "ZIGBEE_DEVICE_JOINED" -> {
                boolean authorized = edge.getZigbeePermitJoinUntil() != null
                        && Instant.now().isBefore(edge.getZigbeePermitJoinUntil().plus(PermitJoinPolicy.GRACE));
                deviceEvents.record(edge.getHomeId(), null, edge.getId(), DeviceEventType.ZIGBEE_JOINED, details);
                audit.record(edge.getHomeId(), "EDGE:" + edge.getName(), "ZIGBEE_DEVICE_JOINED", "EDGE",
                        edge.getId().toString(), details);
                alerts.raiseOnce(edge.getHomeId(), null, AlertType.ZIGBEE_PAIRING,
                        authorized ? AlertSeverity.INFO : AlertSeverity.CRITICAL,
                        authorized ? "Nuevo dispositivo Zigbee unido" : "Dispositivo Zigbee unido sin autorización",
                        details + (authorized ? ". Revísalo en Ecosistema IoT antes de incorporarlo."
                                : ". El emparejamiento no estaba autorizado por HomeIA: revisa Zigbee2MQTT."), null);
            }
            case "ZIGBEE_DEVICE_LEFT" ->
                    deviceEvents.record(edge.getHomeId(), null, edge.getId(), DeviceEventType.ZIGBEE_LEFT, details);
            case "DEVICE_UNREACHABLE", "DEVICE_ERROR" -> {
                UUID deviceId = uuid(body.path("deviceId").asText(""));
                if (deviceId != null && deviceService.devicesOfEdge(edge.getId()).stream().anyMatch(d -> d.getId().equals(deviceId))) {
                    deviceEvents.record(edge.getHomeId(), deviceId, edge.getId(), DeviceEventType.ERROR, details);
                }
            }
            default -> log.debug("Evento de Edge no reconocido: {}", type);
        }
    }

    private void markOnline(EdgeNodeEntity edge, Instant now) {
        if (edge.getStatus() != EdgeStatus.ONLINE) {
            boolean wasOffline = edge.getStatus() == EdgeStatus.OFFLINE;
            edge.setStatus(EdgeStatus.ONLINE);
            audit.record(edge.getHomeId(), "EDGE:" + edge.getName(), wasOffline ? "EDGE_RECONNECTED" : "EDGE_CONNECTED",
                    "EDGE", edge.getId().toString(), edge.getVersion());
        }
        if (edge.getLastHeartbeatAt() == null) edge.setLastHeartbeatAt(now);
    }

    /** Envía al Edge su inventario: qué dispositivos controla, cómo y con qué credenciales (cifradas). */
    public void sync(EdgeNodeEntity edge) {
        List<Map<String, Object>> devices = new ArrayList<>();
        for (DeviceEntity device : deviceService.devicesOfEdge(edge.getId())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deviceId", device.getId().toString());
            item.put("provider", device.getProvider().name());
            item.put("address", DeviceCommandService.address(device));
            item.put("type", device.getType().name());
            item.put("capabilities", Json.read(device.getCapabilitiesJson() == null ? "[]" : device.getCapabilitiesJson()));
            credentials.plaintext(device.getId())
                    .ifPresent(plain -> item.put("credentials", registry.encryptFor(edge, Json.write(plain))));
            devices.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("devices", devices);
        body.put("pollSeconds", 30);
        body.put("permitJoinUntil", edge.getZigbeePermitJoinUntil() == null ? null : edge.getZigbeePermitJoinUntil().getEpochSecond());
        try {
            registry.send(edge, "sync", body);
        } catch (RuntimeException e) {
            log.warn("No se pudo sincronizar el Edge {}: {}", edge.getName(), e.getMessage());
        }
    }

    /** Tras (re)conectar al broker, todos los Edge reciben su inventario de nuevo. */
    @EventListener
    @Transactional(readOnly = true)
    public void onMqttConnected(MqttConnectedEvent event) {
        edgeRepository.findByStatusNot(EdgeStatus.REVOKED).forEach(this::sync);
    }

    /** Re-sincronización manual (botón en la interfaz o después de editar un dispositivo). */
    @Transactional(readOnly = true)
    public void resync(UUID homeId, UUID edgeId) {
        sync(registry.get(homeId, edgeId));
    }

    @Transactional(readOnly = true)
    public void resyncForDevice(DeviceEntity device) {
        if (device.getEdgeId() == null || !registry.mqttReady()) return;
        edgeRepository.findById(device.getEdgeId())
                .filter(e -> e.getStatus() == EdgeStatus.ONLINE)
                .ifPresent(this::sync);
    }

    /** Marca OFFLINE los Edge sin heartbeat y vuelve a verificar el cierre del emparejamiento Zigbee. */
    @Scheduled(fixedDelay = 15_000, initialDelay = 30_000)
    @Transactional
    public void watchdog() {
        Instant now = Instant.now();
        Duration timeout = Duration.ofSeconds(registry.config().heartbeatTimeout());
        for (EdgeNodeEntity edge : edgeRepository.findByStatusIn(EnumSet.of(EdgeStatus.ONLINE))) {
            if (edge.getLastHeartbeatAt() == null || edge.getLastHeartbeatAt().plus(timeout).isBefore(now)) {
                edge.setStatus(EdgeStatus.OFFLINE);
                edge.setUpdatedAt(now);
                audit.record(edge.getHomeId(), "SISTEMA", "EDGE_OFFLINE", "EDGE", edge.getId().toString(), edge.getName());
                alerts.raiseOnce(edge.getHomeId(), null, AlertType.EDGE_OFFLINE, AlertSeverity.WARNING,
                        "HomeIA Edge sin conexión",
                        edge.getName() + " no envía heartbeat desde hace más de " + timeout.toSeconds()
                                + " s. Los dispositivos Zigbee/Wi-Fi locales de ese Edge no se pueden controlar.", null);
            }
        }
    }

    private void enforcePermitJoin(EdgeNodeEntity edge, Instant now) {
        if (PermitJoinPolicy.evaluate(edge.isZigbeePermitJoin(), edge.getZigbeePermitJoinUntil(), now)
                == PermitJoinPolicy.Verdict.OK) {
            return;
        }
        try {
            registry.send(edge, "permit_join", Map.of("seconds", 0));
        } catch (RuntimeException e) {
            log.warn("No se pudo forzar el cierre de permit_join en {}: {}", edge.getName(), e.getMessage());
        }
        edge.setZigbeePermitJoinUntil(null);
        audit.record(edge.getHomeId(), "SISTEMA", "ZIGBEE_PERMIT_JOIN_FORCED_CLOSE", "EDGE", edge.getId().toString(), edge.getName());
        alerts.raiseOnce(edge.getHomeId(), null, AlertType.ZIGBEE_PAIRING, AlertSeverity.WARNING,
                "Emparejamiento Zigbee abierto fuera de plazo",
                "Zigbee2MQTT en " + edge.getName() + " seguía aceptando dispositivos nuevos sin autorización vigente. "
                        + "HomeIA envió el cierre; verifica que permit_join quede desactivado.", null);
    }

    private void securityAlert(EdgeNodeEntity edge, String message) {
        Instant now = Instant.now();
        Instant last = lastSecurityAlert.get(edge.getId());
        if (last != null && last.plus(SECURITY_ALERT_COOLDOWN).isAfter(now)) return;
        lastSecurityAlert.put(edge.getId(), now);
        audit.record(edge.getHomeId(), "SISTEMA", "EDGE_INVALID_SIGNATURE", "EDGE", edge.getId().toString(), edge.getName());
        alerts.raiseOnce(edge.getHomeId(), null, AlertType.SECURITY, AlertSeverity.CRITICAL,
                "Firma inválida de HomeIA Edge", message, null);
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
