package com.homeia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.domain.ConnectionStatus;
import com.homeia.domain.DeviceType;
import com.homeia.domain.PowerState;
import com.homeia.integration.DeviceReportEvent;
import com.homeia.integration.Json;
import com.homeia.integration.MqttGateway;
import com.homeia.persistence.DeviceEntity;
import com.homeia.persistence.DeviceRepository;
import com.homeia.persistence.EnergyReadingEntity;
import com.homeia.persistence.EnergyReadingRepository;
import com.homeia.persistence.RoomRepository;
import com.homeia.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final RoomRepository roomRepository;
    private final EnergyReadingRepository energyRepository;
    private final MqttGateway mqttGateway;
    private final RoomService roomService;
    private final AuditService auditService;

    public enum Command { ON, OFF, TOGGLE, BRIGHTNESS, STATUS }

    @Transactional(readOnly = true)
    public List<DeviceEntity> list(UUID homeId, UUID roomId, DeviceType type, ConnectionStatus connection) {
        return deviceRepository.findByHomeIdOrderByNameAsc(homeId).stream()
                .filter(d -> roomId == null || roomId.equals(d.getRoomId()))
                .filter(d -> type == null || type == d.getType())
                .filter(d -> connection == null || connection == d.getConnectionStatus())
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceEntity get(UUID homeId, UUID deviceId) {
        return deviceRepository.findByIdAndHomeId(deviceId, homeId).orElseThrow(() -> ApiException.notFound("Dispositivo"));
    }

    @Transactional
    public DeviceEntity create(AuthPrincipal principal, String name, DeviceType type, UUID roomId, boolean critical) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("El nombre del dispositivo es obligatorio");
        }
        if (type == null) {
            throw ApiException.badRequest("El tipo de dispositivo es obligatorio");
        }
        if (roomId != null) {
            roomService.get(principal.homeId(), roomId);
        }
        DeviceEntity device = new DeviceEntity();
        device.setHomeId(principal.homeId());
        device.setRoomId(roomId);
        device.setName(name.trim());
        device.setType(type);
        device.setCritical(critical);
        deviceRepository.save(device);
        auditService.record(principal, "DEVICE_CREATED", "DEVICE", device.getId().toString(), device.getName());
        return device;
    }

    @Transactional
    public DeviceEntity update(AuthPrincipal principal, UUID deviceId, String name, UUID roomId,
                               boolean clearRoom, Boolean critical) {
        DeviceEntity device = get(principal.homeId(), deviceId);
        if (name != null && !name.isBlank()) {
            device.setName(name.trim());
        }
        if (clearRoom) {
            device.setRoomId(null);
        } else if (roomId != null) {
            roomService.get(principal.homeId(), roomId);
            device.setRoomId(roomId);
        }
        if (critical != null) {
            device.setCritical(critical);
        }
        device.setUpdatedAt(Instant.now());
        auditService.record(principal, "DEVICE_UPDATED", "DEVICE", deviceId.toString(), device.getName());
        return device;
    }

    @Transactional
    public void delete(AuthPrincipal principal, UUID deviceId) {
        DeviceEntity device = get(principal.homeId(), deviceId);
        deviceRepository.delete(device);
        auditService.record(principal, "DEVICE_DELETED", "DEVICE", deviceId.toString(), device.getName());
    }

    /**
     * Registra un dispositivo descubierto por Bluetooth. Si ya existe uno con ese id Bluetooth en la casa,
     * lo devuelve sin duplicarlo (y actualiza habitación/crítico si se envían).
     */
    @Transactional
    public DeviceEntity registerBluetooth(AuthPrincipal principal, String bluetoothId, String name, DeviceType type,
                                          UUID roomId, boolean critical) {
        if (bluetoothId == null || bluetoothId.isBlank() || bluetoothId.length() > 120) {
            throw ApiException.badRequest("El identificador Bluetooth es obligatorio (máximo 120 caracteres)");
        }
        DeviceEntity device = deviceRepository.findByHomeIdAndBluetoothId(principal.homeId(), bluetoothId.trim())
                .orElse(null);
        if (device != null) {
            if (roomId != null) {
                roomService.get(principal.homeId(), roomId);
                device.setRoomId(roomId);
            }
            device.setUpdatedAt(Instant.now());
            auditService.record(principal, "DEVICE_BLUETOOTH_LINKED", "DEVICE", device.getId().toString(), device.getName());
            return device;
        }
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("El nombre del dispositivo es obligatorio");
        }
        if (type == null) {
            throw ApiException.badRequest("El tipo de dispositivo es obligatorio");
        }
        if (roomId != null) {
            roomService.get(principal.homeId(), roomId);
        }
        device = new DeviceEntity();
        device.setHomeId(principal.homeId());
        device.setRoomId(roomId);
        device.setName(name.trim());
        device.setType(type);
        device.setCritical(critical);
        device.setBluetoothId(bluetoothId.trim());
        deviceRepository.save(device);
        auditService.record(principal, "DEVICE_BLUETOOTH_REGISTERED", "DEVICE", device.getId().toString(), device.getName());
        return device;
    }

    /** Estado enviado desde la app (conexión Bluetooth del navegador). Misma regla que el reporte MQTT. */
    @Transactional
    public DeviceEntity reportState(AuthPrincipal principal, UUID deviceId, String payload) {
        DeviceEntity device = get(principal.homeId(), deviceId);
        applyReport(device, payload);
        return device;
    }

    /**
     * Envía un comando al dispositivo por MQTT. El estado en base de datos se actualiza cuando el
     * propio dispositivo reporta el cambio: no se asume éxito antes de confirmación.
     */
    @Transactional
    public DeviceEntity command(AuthPrincipal principal, UUID deviceId, Command command, Integer brightness) {
        DeviceEntity device = get(principal.homeId(), deviceId);
        if (command == Command.BRIGHTNESS && (brightness == null || brightness < 0 || brightness > 100)) {
            throw ApiException.badRequest("El brillo debe estar entre 0 y 100");
        }
        if (command == Command.BRIGHTNESS && device.getType() != DeviceType.LIGHT) {
            throw ApiException.badRequest("Solo las luces admiten brillo");
        }
        if (!mqttGateway.enabled()) {
            throw ApiException.unavailable("El broker MQTT no está habilitado (MQTT_ENABLED=false)");
        }
        if (!mqttGateway.connected()) {
            throw ApiException.unavailable("El broker MQTT no está conectado");
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("command", command.name());
        if (brightness != null) {
            message.put("brightness", brightness);
        }
        message.put("requestedAt", Instant.now().toString());
        message.put("requestedBy", principal.email());
        try {
            mqttGateway.publishCommand(deviceId, Json.write(message));
        } catch (IllegalStateException e) {
            throw ApiException.unavailable(e.getMessage());
        }
        auditService.record(principal, "DEVICE_COMMAND", "DEVICE", deviceId.toString(),
                command.name() + (brightness != null ? " " + brightness + "%" : ""));
        return device;
    }

    /**
     * Comando emitido por el motor de automatización (sin usuario). Devuelve false si no se pudo enviar.
     * No lanza excepciones: se usa dentro de la transacción del ciclo y una excepción la marcaría como revertida.
     */
    public boolean commandAutomatically(UUID homeId, UUID deviceId, String deviceName, Command command) {
        if (!mqttGateway.enabled() || !mqttGateway.connected()) {
            return false;
        }
        try {
            mqttGateway.publishCommand(deviceId, Json.write(Map.of(
                    "command", command.name(),
                    "requestedAt", Instant.now().toString(),
                    "requestedBy", "AUTOMATIZACION")));
        } catch (IllegalStateException e) {
            log.warn("Comando automático no enviado a {}: {}", deviceId, e.getMessage());
            return false;
        }
        auditService.record(homeId, "AUTOMATIZACION", "DEVICE_COMMAND", "DEVICE", deviceId.toString(),
                command.name() + " " + deviceName);
        return true;
    }

    /** Procesa el estado que publica un dispositivo: {"powerState":"ON","consumptionWatts":12.5,...} */
    @EventListener
    @Transactional
    public void onDeviceReport(DeviceReportEvent event) {
        deviceRepository.findById(event.deviceId()).ifPresentOrElse(
                device -> applyReport(device, event.payload()),
                () -> log.debug("Reporte MQTT de un dispositivo desconocido: {}", event.deviceId()));
    }

    private void applyReport(DeviceEntity device, String payload) {
        JsonNode report = Json.read(payload);
        Instant now = Instant.now();
        device.setLastSeenAt(now);
        device.setConnectionStatus(ConnectionStatus.ONLINE);

        if (report.hasNonNull("powerState")) {
            PowerState state = PowerState.valueOf(report.get("powerState").asText().toUpperCase());
            if (state != device.getPowerState()) {
                device.setPowerState(state);
                device.setLastStatusChangeAt(now);
            }
        }
        if (report.hasNonNull("brightness")) {
            device.setBrightness(report.get("brightness").asInt());
        }
        if (report.hasNonNull("consumptionWatts")) {
            double watts = report.get("consumptionWatts").asDouble();
            device.setConsumptionWatts(watts);
            energyRepository.save(new EnergyReadingEntity(device.getHomeId(), device.getId(), watts, now));
        }
        if (report.hasNonNull("presence") && device.getRoomId() != null) {
            boolean present = report.get("presence").asBoolean();
            device.setPresence(present);
            roomService.observePresence(device.getHomeId(), device.getRoomId(), present, now);
        }
        device.setUpdatedAt(now);
    }

    /** Estado desconectado: lo marca el motor de automatización cuando el dispositivo deja de reportar. */
    @Transactional
    public void markOffline(DeviceEntity device) {
        if (device.getConnectionStatus() != ConnectionStatus.OFFLINE) {
            device.setConnectionStatus(ConnectionStatus.OFFLINE);
            device.setUpdatedAt(Instant.now());
        }
    }
}
