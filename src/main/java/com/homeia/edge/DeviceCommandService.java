package com.homeia.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.domain.CommandStatus;
import com.homeia.domain.DeviceEventType;
import com.homeia.domain.DeviceProvider;
import com.homeia.domain.EdgeStatus;
import com.homeia.integration.Json;
import com.homeia.persistence.DeviceCommandEntity;
import com.homeia.persistence.DeviceCommandRepository;
import com.homeia.persistence.DeviceEntity;
import com.homeia.persistence.DeviceRepository;
import com.homeia.persistence.EdgeNodeEntity;
import com.homeia.persistence.EdgeNodeRepository;
import com.homeia.service.CommandRetryPolicy;
import com.homeia.service.DeviceCredentialService;
import com.homeia.service.DeviceEventService;
import com.homeia.service.driver.DeviceCommand;
import com.homeia.service.driver.DeviceDriverException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Órdenes a dispositivos LAN a través de HomeIA Edge, con requestId, expiración, confirmación y
 * reintentos idempotentes (el Edge deduplica por requestId y repite el resultado ya conocido).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCommandService {

    /** Resultado de una confirmación: la orden y, si el Edge lo leyó, el estado del dispositivo. */
    public record Ack(DeviceCommandEntity command, JsonNode state) {
    }

    private final DeviceCommandRepository repository;
    private final EdgeNodeRepository edgeRepository;
    private final DeviceRepository deviceRepository;
    private final EdgeRegistryService registry;
    private final DeviceCredentialService credentials;
    private final DeviceEventService events;

    /** Motivo por el que no se puede enviar ahora al Edge del dispositivo, o vacío. */
    @Transactional(readOnly = true)
    public Optional<String> unavailableReason(DeviceEntity device) {
        if (!registry.mqttReady()) return Optional.of("El backend no está conectado al broker MQTT de HomeIA Edge");
        if (device.getEdgeId() == null) {
            return Optional.of("El dispositivo no está asociado a un HomeIA Edge; vuelve a incorporarlo desde Ecosistema IoT");
        }
        EdgeNodeEntity edge = edgeRepository.findById(device.getEdgeId()).orElse(null);
        if (edge == null) return Optional.of("El HomeIA Edge del dispositivo ya no existe");
        if (edge.getStatus() == EdgeStatus.REVOKED) return Optional.of("El HomeIA Edge " + edge.getName() + " está revocado");
        if (edge.getStatus() != EdgeStatus.ONLINE) return Optional.of("El HomeIA Edge " + edge.getName() + " está desconectado");
        return Optional.empty();
    }

    @Transactional
    public DeviceCommandEntity dispatch(DeviceEntity device, DeviceCommand command, Integer brightness, String requestedBy) {
        unavailableReason(device).ifPresent(reason -> {
            throw new DeviceDriverException(reason);
        });
        EdgeNodeEntity edge = edgeRepository.findById(device.getEdgeId()).orElseThrow();
        Instant now = Instant.now();
        DeviceCommandEntity entity = new DeviceCommandEntity();
        entity.setHomeId(device.getHomeId());
        entity.setDeviceId(device.getId());
        entity.setEdgeId(edge.getId());
        entity.setCommand(command.name());
        entity.setBrightness(brightness);
        entity.setRequestedBy(requestedBy == null ? "SISTEMA" : requestedBy);
        entity.setExpiresAt(now.plus(CommandRetryPolicy.TTL));
        repository.save(entity);
        publish(entity, device, edge);
        events.record(device.getHomeId(), device.getId(), edge.getId(), DeviceEventType.COMMAND_SENT,
                command.name() + (brightness != null ? " " + brightness + "%" : "") + " · " + entity.getRequestedBy());
        return entity;
    }

    /** Payload de la orden. Las credenciales viajan cifradas solo para ese Edge. */
    Map<String, Object> payload(DeviceCommandEntity command, DeviceEntity device, EdgeNodeEntity edge) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", command.getId().toString());
        body.put("deviceId", device.getId().toString());
        body.put("provider", device.getProvider().name());
        body.put("address", address(device));
        body.put("command", command.getCommand());
        if (command.getBrightness() != null) body.put("brightness", command.getBrightness());
        body.put("expiresAt", command.getExpiresAt().getEpochSecond());
        body.put("capabilities", Json.read(device.getCapabilitiesJson() == null ? "[]" : device.getCapabilitiesJson()));
        credentials.plaintext(device.getId())
                .ifPresent(plain -> body.put("credentials", registry.encryptFor(edge, Json.write(plain))));
        return body;
    }

    /** Dirección que entiende el Edge: friendly_name en Zigbee, IPv4 LAN en el resto. */
    public static String address(DeviceEntity device) {
        return device.getProvider() == DeviceProvider.ZIGBEE ? device.getExternalId() : device.getNetworkAddress();
    }

    private void publish(DeviceCommandEntity command, DeviceEntity device, EdgeNodeEntity edge) {
        try {
            registry.send(edge, "command", payload(command, device, edge));
            command.setStatus(CommandStatus.SENT);
        } catch (RuntimeException e) {
            // Se reintenta en el siguiente ciclo mientras no expire.
            log.warn("No se pudo publicar la orden {} al Edge {}: {}", command.getId(), edge.getName(), e.getMessage());
            command.setError(truncate(e.getMessage()));
        }
        command.setAttempts(command.getAttempts() + 1);
        command.setLastSentAt(Instant.now());
    }

    /** Reintenta las órdenes sin confirmar y expira las vencidas. */
    @Scheduled(fixedDelay = 3_000, initialDelay = 10_000)
    @Transactional
    public void retryPending() {
        List<DeviceCommandEntity> open = repository.findByStatusIn(EnumSet.of(CommandStatus.PENDING, CommandStatus.SENT));
        Instant now = Instant.now();
        for (DeviceCommandEntity command : open) {
            switch (CommandRetryPolicy.decide(command.getAttempts(), command.getLastSentAt(), command.getExpiresAt(), now)) {
                case WAIT -> { }
                case EXPIRE -> {
                    command.setStatus(CommandStatus.EXPIRED);
                    command.setCompletedAt(now);
                    if (command.getError() == null) command.setError("Sin confirmación del Edge");
                    events.record(command.getHomeId(), command.getDeviceId(), command.getEdgeId(),
                            DeviceEventType.COMMAND_EXPIRED, command.getCommand() + ": " + command.getError());
                }
                case RESEND -> resend(command);
            }
        }
    }

    private void resend(DeviceCommandEntity command) {
        if (command.getEdgeId() == null) {
            command.setStatus(CommandStatus.FAILED);
            command.setError("El Edge del dispositivo ya no existe");
            command.setCompletedAt(Instant.now());
            return;
        }
        EdgeNodeEntity edge = edgeRepository.findById(command.getEdgeId()).orElse(null);
        DeviceEntity device = deviceLookup(command);
        if (edge == null || device == null) {
            command.setStatus(CommandStatus.FAILED);
            command.setError("Dispositivo o Edge eliminados");
            command.setCompletedAt(Instant.now());
            return;
        }
        publish(command, device, edge);
    }

    private DeviceEntity deviceLookup(DeviceCommandEntity command) {
        return deviceRepository.findById(command.getDeviceId()).orElse(null);
    }

    /** Confirmación del Edge: {"requestId","status":"OK|FAILED|EXPIRED|DUPLICATE","error","state":{…}} */
    @Transactional
    public Optional<Ack> onAck(EdgeNodeEntity edge, JsonNode body) {
        UUID requestId;
        try {
            requestId = UUID.fromString(body.path("requestId").asText(""));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        DeviceCommandEntity command = repository.findById(requestId).orElse(null);
        if (command == null || !edge.getId().equals(command.getEdgeId())) {
            log.warn("Confirmación de una orden desconocida o de otro Edge: {}", requestId);
            return Optional.empty();
        }
        JsonNode state = body.path("state").isObject() ? body.get("state") : null;
        if (command.getStatus().finished()) {
            return Optional.of(new Ack(command, state)); // duplicado de un reintento: el estado sigue sirviendo
        }
        String status = body.path("status").asText("");
        Instant now = Instant.now();
        command.setCompletedAt(now);
        if ("OK".equals(status) || "DUPLICATE".equals(status)) {
            command.setStatus(CommandStatus.CONFIRMED);
            command.setError(null);
            events.record(command.getHomeId(), command.getDeviceId(), edge.getId(), DeviceEventType.COMMAND_CONFIRMED,
                    command.getCommand() + " confirmado en " + (command.getAttempts()) + " intento(s)");
        } else {
            command.setStatus("EXPIRED".equals(status) ? CommandStatus.EXPIRED : CommandStatus.FAILED);
            command.setError(truncate(body.path("error").asText("Error desconocido en el Edge")));
            events.record(command.getHomeId(), command.getDeviceId(), edge.getId(),
                    command.getStatus() == CommandStatus.EXPIRED ? DeviceEventType.COMMAND_EXPIRED : DeviceEventType.COMMAND_FAILED,
                    command.getCommand() + ": " + command.getError());
        }
        return Optional.of(new Ack(command, state));
    }

    @Transactional(readOnly = true)
    public List<DeviceCommandEntity> recent(UUID homeId, UUID deviceId, int limit) {
        return repository.findByDeviceIdAndHomeIdOrderByCreatedAtDesc(deviceId, homeId,
                PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
