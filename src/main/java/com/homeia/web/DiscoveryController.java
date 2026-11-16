package com.homeia.web;

import com.homeia.domain.DeviceType;
import com.homeia.persistence.DeviceEntity;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.DeviceDiscoveryService;
import com.homeia.service.DeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/discovery")
@PreAuthorize("hasRole('ADMIN')")
public class DiscoveryController {
    private final DeviceDiscoveryService discovery; private final DeviceService devices;
    public DiscoveryController(DeviceDiscoveryService discovery, DeviceService devices) { this.discovery=discovery; this.devices=devices; }
    public record ClaimBody(@NotNull DeviceType type, UUID roomId, Boolean critical) {}
    @PostMapping("/scan") public Map<String,Object> scan() { return discovery.scan(); }
    @PostMapping("/zigbee/permit-join") public Map<String,Object> permitJoin(@RequestParam(defaultValue = "60") int seconds) {
        if (seconds < 1 || seconds > 120) throw com.homeia.service.ApiException.badRequest("El tiempo debe estar entre 1 y 120 segundos");
        // El permiso de incorporación se limita temporalmente y requiere rol ADMIN.
        // Zigbee2MQTT aplica la política real del coordinador.
        return discoveryPermitJoin(seconds);
    }
    private Map<String,Object> discoveryPermitJoin(int seconds) { return discovery.permitZigbeeJoin(seconds); }
    @PostMapping("/zigbee/permit-join/close") public Map<String,Object> closePermitJoin() { return discovery.permitZigbeeJoin(0); }
    @GetMapping("/candidates") public List<DeviceDiscoveryService.Candidate> candidates() { return discovery.list(); }
    @PostMapping("/candidates/{id}/claim") public DeviceController.DeviceDto claim(@AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id, @Valid @RequestBody ClaimBody body) {
        DeviceDiscoveryService.Candidate c = discovery.get(id).orElseThrow(() -> com.homeia.service.ApiException.notFound("Candidato de descubrimiento"));
        var provider = discovery.provider(c); var protocol = discovery.protocol(c);
        if (provider != com.homeia.domain.DeviceProvider.SHELLY && provider != com.homeia.domain.DeviceProvider.TASMOTA
                && provider != com.homeia.domain.DeviceProvider.ZIGBEE)
            throw com.homeia.service.ApiException.badRequest("Este proveedor aún no tiene un controlador de comandos habilitado");
        DeviceEntity d = devices.registerExternal(principal,c.name(),body.type(),body.roomId(),Boolean.TRUE.equals(body.critical()),
                provider,c.address(),protocol,c.manufacturer(),c.model(),com.homeia.integration.Json.write(c.capabilities()),
                protocol == com.homeia.domain.DeviceProtocol.WIFI ? c.address() : null);
        return DeviceController.DeviceDto.from(d);
    }
}
