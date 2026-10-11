package com.homeia.web;

import com.homeia.domain.ConnectionStatus;
import com.homeia.domain.DeviceType;
import com.homeia.domain.PowerState;
import com.homeia.persistence.DeviceEntity;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.DeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.homeia.integration.Json;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    public record DeviceDto(UUID id, String name, UUID roomId, DeviceType type, ConnectionStatus connectionStatus,
                            PowerState powerState, Integer brightness, double consumptionWatts, boolean critical,
                            Boolean presence, Instant lastPresenceAt, Instant lastStatusChangeAt, Instant lastSeenAt,
                            String bluetoothId) {
        static DeviceDto from(DeviceEntity d) {
            return new DeviceDto(d.getId(), d.getName(), d.getRoomId(), d.getType(), d.getConnectionStatus(),
                    d.getPowerState(), d.getBrightness(), d.getConsumptionWatts(), d.isCritical(), d.getPresence(),
                    d.getLastPresenceAt(), d.getLastStatusChangeAt(), d.getLastSeenAt(), d.getBluetoothId());
        }
    }

    public record BluetoothBody(@NotBlank String bluetoothId, @NotBlank String name, @NotNull DeviceType type,
                                UUID roomId, boolean critical) {
    }

    public record DeviceBody(@NotBlank String name, @NotNull DeviceType type, UUID roomId, boolean critical) {
    }

    public record UpdateDeviceBody(String name, UUID roomId, boolean clearRoom, Boolean critical) {
    }

    public record CommandBody(@NotNull DeviceService.Command command, Integer brightness) {
    }

    @GetMapping
    public List<DeviceDto> list(@AuthenticationPrincipal AuthPrincipal principal,
                                @RequestParam(required = false) UUID roomId,
                                @RequestParam(required = false) DeviceType type,
                                @RequestParam(required = false) ConnectionStatus connection) {
        return deviceService.list(principal.homeId(), roomId, type, connection).stream().map(DeviceDto::from).toList();
    }

    @GetMapping("/{id}")
    public DeviceDto get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return DeviceDto.from(deviceService.get(principal.homeId(), id));
    }

    @PostMapping
    public DeviceDto create(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody DeviceBody body) {
        return DeviceDto.from(deviceService.create(principal, body.name(), body.type(), body.roomId(), body.critical()));
    }

    @PutMapping("/{id}")
    public DeviceDto update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                            @RequestBody UpdateDeviceBody body) {
        return DeviceDto.from(deviceService.update(principal, id, body.name(), body.roomId(), body.clearRoom(),
                body.critical()));
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        deviceService.delete(principal, id);
    }

    /** Registra (o vincula, si ya existe) un dispositivo descubierto por Bluetooth. */
    @PostMapping("/bluetooth")
    public DeviceDto registerBluetooth(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody BluetoothBody body) {
        return DeviceDto.from(deviceService.registerBluetooth(principal, body.bluetoothId(), body.name(), body.type(),
                body.roomId(), body.critical()));
    }

    /**
     * Estado reportado desde la app por Bluetooth, con el mismo formato que el MQTT:
     * {"powerState":"ON","consumptionWatts":12.5,"brightness":80,"presence":true}
     */
    @PostMapping("/{id}/state")
    public DeviceDto reportState(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                 @RequestBody Map<String, Object> body) {
        return DeviceDto.from(deviceService.reportState(principal, id, Json.write(body)));
    }

    /** Envía ON / OFF / TOGGLE / BRIGHTNESS / STATUS por MQTT. El estado final llega en el reporte del dispositivo. */
    @PostMapping("/{id}/commands")
    public DeviceDto command(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                             @Valid @RequestBody CommandBody body) {
        return DeviceDto.from(deviceService.command(principal, id, body.command(), body.brightness()));
    }
}
