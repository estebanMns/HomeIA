package com.homeia.web;

import com.homeia.domain.DeviceEventType;
import com.homeia.domain.DeviceProtocol;
import com.homeia.domain.DeviceType;
import com.homeia.edge.EdgeInboundService;
import com.homeia.integration.Json;
import com.homeia.persistence.DeviceEntity;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.ApiException;
import com.homeia.service.DeviceDiscoveryService;
import com.homeia.service.DeviceEventService;
import com.homeia.service.DeviceService;
import com.homeia.service.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Descubrimiento LAN a través de HomeIA Edge, incorporación autorizada y emparejamiento Zigbee temporal. */
@RestController
@RequestMapping("/api/discovery")
@PreAuthorize("hasRole('ADMIN')")
public class DiscoveryController {

    private final DeviceDiscoveryService discovery;
    private final DeviceService devices;
    private final DeviceEventService deviceEvents;
    private final EdgeInboundService edgeInbound;
    private final RateLimiter limiter = new RateLimiter(10, Duration.ofMinutes(1));

    public DiscoveryController(DeviceDiscoveryService discovery, DeviceService devices, DeviceEventService deviceEvents,
                               EdgeInboundService edgeInbound) {
        this.discovery = discovery;
        this.devices = devices;
        this.deviceEvents = deviceEvents;
        this.edgeInbound = edgeInbound;
    }

    public record ClaimBody(@NotNull DeviceType type, UUID roomId, Boolean critical, @Size(max = 120) String name) {
    }

    @PostMapping("/scan")
    public Map<String, Object> scan(@AuthenticationPrincipal AuthPrincipal principal) {
        limit(principal);
        return discovery.scan(principal);
    }

    @PostMapping("/zigbee/permit-join")
    public Map<String, Object> permitJoin(@AuthenticationPrincipal AuthPrincipal principal,
                                          @RequestParam(defaultValue = "60") int seconds,
                                          @RequestParam(required = false) UUID edgeId) {
        limit(principal);
        if (seconds < 1) throw ApiException.badRequest("Usa /zigbee/permit-join/close para cerrar el emparejamiento");
        return discovery.permitZigbeeJoin(principal, edgeId, seconds);
    }

    @PostMapping("/zigbee/permit-join/close")
    public Map<String, Object> closePermitJoin(@AuthenticationPrincipal AuthPrincipal principal,
                                               @RequestParam(required = false) UUID edgeId) {
        return discovery.permitZigbeeJoin(principal, edgeId, 0);
    }

    @GetMapping("/candidates")
    public List<DeviceDiscoveryService.Candidate> candidates(@AuthenticationPrincipal AuthPrincipal principal) {
        return discovery.list(principal.homeId());
    }

    @PostMapping("/candidates/{id}/claim")
    public DeviceController.DeviceDto claim(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable String id, @Valid @RequestBody ClaimBody body) {
        DeviceDiscoveryService.Candidate c = discovery.get(principal.homeId(), id)
                .orElseThrow(() -> ApiException.notFound("Candidato de descubrimiento"));
        var provider = discovery.provider(c);
        var protocol = discovery.protocol(c);
        if (!provider.viaEdge()) {
            throw ApiException.badRequest("Este proveedor no se incorpora desde el descubrimiento local");
        }
        String name = body.name() == null || body.name().isBlank() ? c.name() : body.name().trim();
        DeviceEntity d = devices.registerExternal(principal, name, body.type(), body.roomId(),
                Boolean.TRUE.equals(body.critical()), provider, c.address(), protocol, c.manufacturer(), c.model(),
                Json.write(c.capabilities()), protocol == DeviceProtocol.ZIGBEE ? null : c.address(), c.edgeId());
        deviceEvents.record(principal.homeId(), d.getId(), c.edgeId(), DeviceEventType.DISCOVERED,
                c.provider() + " vía " + c.edgeName() + (c.service() != null ? " (" + c.service() + ")" : ""));
        discovery.forget(principal.homeId(), id);
        edgeInbound.resyncForDevice(d);
        return DeviceController.DeviceDto.from(d);
    }

    private void limit(AuthPrincipal principal) {
        if (!limiter.tryAcquire(principal.userId().toString())) {
            throw ApiException.tooManyRequests("Demasiadas solicitudes seguidas; espera un minuto");
        }
    }
}
