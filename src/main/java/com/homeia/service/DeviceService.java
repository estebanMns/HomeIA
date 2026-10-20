package com.homeia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.domain.ConnectionStatus;
import com.homeia.domain.DeviceProvider;
import com.homeia.domain.DeviceType;
import com.homeia.domain.PowerState;
import com.homeia.integration.DeviceReportEvent;
import com.homeia.integration.Json;
import com.homeia.integration.tuya.TuyaApiException;
import com.homeia.integration.tuya.TuyaCloudDevice;
import com.homeia.integration.tuya.TuyaGateway;
import com.homeia.persistence.DeviceEntity;
import com.homeia.persistence.DeviceRepository;
import com.homeia.persistence.EnergyReadingEntity;
import com.homeia.persistence.EnergyReadingRepository;
import com.homeia.persistence.RoomRepository;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.driver.DeviceCommand;
import com.homeia.service.driver.DeviceDriver;
import com.homeia.service.driver.DeviceDriverException;
import com.homeia.service.driver.DeviceDrivers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final RoomRepository roomRepository;
    private final EnergyReadingRepository energyRepository;
    private final DeviceDrivers drivers;
    private final TuyaGateway tuyaGateway;
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
                               boolean clearRoom, Boolean critical, Double consumptionAlertWatts) {
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
        if (consumptionAlertWatts != null) {
            device.setConsumptionAlertWatts(consumptionAlertWatts > 0 ? consumptionAlertWatts : null);
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
     * Envía un comando al dispositivo por su driver (MQTT o Tuya). El estado en base de datos se actualiza
     * cuando el dispositivo lo confirma: en MQTT cuando publica su estado, en Tuya al leerlo justo después.
     */
    @Transactional
    public DeviceEntity command(AuthPrincipal principal, UUID deviceId, Command command, Integer brightness,
                                boolean confirmCritical) {
        DeviceEntity device = get(principal.homeId(), deviceId);
        boolean turnsOff = command == Command.OFF
                || (command == Command.TOGGLE && device.getPowerState() == PowerState.ON);
        DeviceCommandPolicy.Verdict verdict = DeviceCommandPolicy.evaluate(principal.isAdmin(), device.getType(),
                device.isCritical(), device.getName(), command != Command.STATUS, turnsOff, confirmCritical);
        switch (verdict.outcome()) {
            case INVALID -> throw ApiException.badRequest(verdict.message());
            case FORBIDDEN -> throw ApiException.forbidden(verdict.message());
            case NEEDS_CONFIRMATION -> throw ApiException.conflict(verdict.message());
            case ALLOWED -> { }
        }
        if (command == Command.BRIGHTNESS && (brightness == null || brightness < 0 || brightness > 100)) {
            throw ApiException.badRequest("El brillo debe estar entre 0 y 100");
        }
        if (command == Command.BRIGHTNESS && device.getType() != DeviceType.LIGHT) {
            throw ApiException.badRequest("Solo las luces admiten brillo");
        }
        DeviceDriver driver = drivers.forDevice(device);
        driver.unavailableReason().ifPresent(reason -> {
            throw ApiException.unavailable(reason);
        });
        try {
            driver.send(device, DeviceCommand.valueOf(command.name()), brightness, principal.email())
                    .ifPresent(confirmed -> applyReport(device, Json.write(confirmed)));
        } catch (DeviceDriverException e) {
            throw ApiException.unavailable(e.getMessage());
        }
        auditService.record(principal, "DEVICE_COMMAND", "DEVICE", deviceId.toString(),
                command.name() + (brightness != null ? " " + brightness + "%" : "")
                        + (turnsOff && device.isCritical() ? " (crítico, confirmado)" : ""));
        return device;
    }

    /**
     * Comando emitido por el motor de automatización (sin usuario). Devuelve false si no se pudo enviar.
     * No lanza excepciones: se usa dentro de la transacción del ciclo y una excepción la marcaría como revertida.
     */
    public boolean commandAutomatically(UUID homeId, UUID deviceId, String deviceName, Command command) {
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            return false;
        }
        DeviceDriver driver = drivers.forDevice(device);
        if (driver.unavailableReason().isPresent()) {
            return false;
        }
        try {
            driver.send(device, DeviceCommand.valueOf(command.name()), null, "AUTOMATIZACION")
                    .ifPresent(confirmed -> applyReport(device, Json.write(confirmed)));
        } catch (RuntimeException e) {
            log.warn("Comando automático no enviado a {}: {}", deviceName, e.getMessage());
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

    /** Si el driver puede leer el estado (Tuya), lo aplica de inmediato. */
    private void refreshState(DeviceEntity device, DeviceDriver driver) {
        try {
            driver.readState(device).ifPresent(report -> applyReport(device, Json.write(report)));
        } catch (RuntimeException e) {
            log.debug("No se pudo leer el estado de {} después del comando: {}", device.getName(), e.getMessage());
        }
    }

    // ---------- Dispositivos WiFi de Tuya ----------

    public record TuyaCandidate(TuyaCloudDevice cloud, UUID importedAs) {
    }

    /** Dispositivos de la cuenta Smart Life vinculada, indicando cuáles ya están en esta casa. */
    @Transactional(readOnly = true)
    public List<TuyaCandidate> tuyaCandidates(UUID homeId) {
        List<TuyaCloudDevice> cloud = callTuya(tuyaGateway::listDevices);
        Map<String, UUID> imported = deviceRepository.findByHomeIdOrderByNameAsc(homeId).stream()
                .filter(d -> d.getProvider() == DeviceProvider.TUYA && d.getExternalId() != null)
                .collect(Collectors.toMap(DeviceEntity::getExternalId, DeviceEntity::getId, (a, b) -> a));
        return cloud.stream().map(c -> new TuyaCandidate(c, imported.get(c.id()))).toList();
    }

    /** Agrega a la casa un dispositivo de la cuenta Smart Life y lee su estado actual. */
    @Transactional
    public DeviceEntity importTuya(AuthPrincipal principal, String externalId, String name, DeviceType type,
                                   UUID roomId, boolean critical) {
        if (externalId == null || externalId.isBlank() || externalId.length() > 64) {
            throw ApiException.badRequest("El id del dispositivo de Tuya es obligatorio");
        }
        if (type == null || !type.switchable()) {
            throw ApiException.badRequest("El tipo debe ser luz o tomacorriente");
        }
        String id = externalId.trim();
        if (deviceRepository.findByHomeIdAndProviderAndExternalId(principal.homeId(), DeviceProvider.TUYA, id).isPresent()) {
            throw ApiException.conflict("Ese dispositivo ya está agregado a la casa");
        }
        TuyaCloudDevice cloud = callTuya(tuyaGateway::listDevices).stream()
                .filter(d -> d.id().equals(id))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Dispositivo de Tuya"));
        if (roomId != null) {
            roomService.get(principal.homeId(), roomId);
        }
        DeviceEntity device = new DeviceEntity();
        device.setHomeId(principal.homeId());
        device.setRoomId(roomId);
        device.setName(name == null || name.isBlank() ? cloud.name() : name.trim());
        device.setType(type);
        device.setCritical(critical);
        device.setProvider(DeviceProvider.TUYA);
        device.setExternalId(id);
        deviceRepository.save(device);
        auditService.record(principal, "DEVICE_TUYA_IMPORTED", "DEVICE", device.getId().toString(),
                device.getName() + " (" + cloud.productName() + ")");
        if (cloud.online()) {
            refreshState(device, drivers.forDevice(device));
        }
        return device;
    }

    /**
     * Lee el estado de todos los dispositivos Tuya: conexión según la nube y, si están conectados,
     * encendido, consumo y brillo. Devuelve cuántos se actualizaron.
     */
    @Transactional
    public int syncTuyaDevices() {
        List<DeviceEntity> devices = deviceRepository.findByProvider(DeviceProvider.TUYA);
        if (devices.isEmpty() || !tuyaGateway.configured()) {
            return 0;
        }
        Map<String, TuyaCloudDevice> cloud = tuyaGateway.listDevices().stream()
                .collect(Collectors.toMap(TuyaCloudDevice::id, Function.identity(), (a, b) -> a));
        DeviceDriver driver = drivers.forDevice(devices.get(0));
        int updated = 0;
        for (DeviceEntity device : devices) {
            TuyaCloudDevice info = cloud.get(device.getExternalId());
            if (info == null || !info.online()) {
                markOffline(device);
                continue;
            }
            try {
                driver.readState(device).ifPresent(report -> applyReport(device, Json.write(report)));
                updated++;
            } catch (RuntimeException e) {
                log.warn("No se pudo leer el estado de {} en Tuya: {}", device.getName(), e.getMessage());
            }
        }
        return updated;
    }

    private <T> T callTuya(java.util.function.Supplier<T> call) {
        if (!tuyaGateway.configured()) {
            throw ApiException.unavailable("Tuya no está configurado: agrega TUYA_ACCESS_ID y TUYA_ACCESS_SECRET al .env y reinicia el backend");
        }
        try {
            return call.get();
        } catch (TuyaApiException e) {
            throw ApiException.unavailable(e.getMessage());
        }
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
