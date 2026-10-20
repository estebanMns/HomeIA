package com.homeia.web;

import com.homeia.domain.DeviceType;
import com.homeia.integration.MqttGateway;
import com.homeia.integration.tuya.TuyaGateway;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.DeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dispositivos por WiFi: Tuya / Smart Life (por la nube de Tuya) y dispositivos propios por MQTT. */
@RestController
@RequestMapping("/api/wifi")
public class WifiController {

    private final DeviceService deviceService;
    private final TuyaGateway tuya;
    private final MqttGateway mqtt;

    public WifiController(DeviceService deviceService, TuyaGateway tuya, MqttGateway mqtt) {
        this.deviceService = deviceService;
        this.tuya = tuya;
        this.mqtt = mqtt;
    }

    public record StatusDto(boolean tuyaConfigured, String tuyaRegion, String tuyaEndpoint,
                            boolean mqttEnabled, boolean mqttConnected) {
    }

    public record TuyaDeviceDto(String id, String name, String category, String productName, boolean online,
                                UUID importedAs) {
    }

    public record ImportBody(@NotBlank @Size(max = 64) String externalId, @Size(max = 120) String name,
                             @NotNull DeviceType type, UUID roomId, boolean critical) {
    }

    @GetMapping("/status")
    public StatusDto status() {
        return new StatusDto(tuya.configured(), tuya.region(), tuya.baseUrl(), mqtt.enabled(), mqtt.connected());
    }

    /** Dispositivos de la cuenta Smart Life vinculada al proyecto de Tuya. */
    @GetMapping("/tuya/devices")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TuyaDeviceDto> tuyaDevices(@AuthenticationPrincipal AuthPrincipal principal) {
        return deviceService.tuyaCandidates(principal.homeId()).stream()
                .map(c -> new TuyaDeviceDto(c.cloud().id(), c.cloud().name(), c.cloud().category(),
                        c.cloud().productName(), c.cloud().online(), c.importedAs()))
                .toList();
    }

    @PostMapping("/tuya/import")
    @PreAuthorize("hasRole('ADMIN')")
    public DeviceController.DeviceDto importTuya(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody ImportBody body) {
        return DeviceController.DeviceDto.from(deviceService.importTuya(principal, body.externalId(), body.name(),
                body.type(), body.roomId(), body.critical()));
    }

    /** Lee ahora el estado de todos los dispositivos Tuya, sin esperar al siguiente ciclo. */
    @PostMapping("/tuya/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> syncNow() {
        return Map.of("updated", deviceService.syncTuyaDevices());
    }
}
