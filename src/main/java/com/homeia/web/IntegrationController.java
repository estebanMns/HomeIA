package com.homeia.web;

import com.homeia.security.AuthPrincipal;
import com.homeia.service.DeviceEventService;
import com.homeia.service.IntegrationStatusService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Estado por integración y diagnóstico paso a paso para el asistente de configuración. */
@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

    private final IntegrationStatusService status;
    private final DeviceEventService events;

    public IntegrationController(IntegrationStatusService status, DeviceEventService events) {
        this.status = status;
        this.events = events;
    }

    public record EventDto(UUID id, UUID deviceId, UUID edgeId, String type, String details, Instant createdAt) {
    }

    @GetMapping("/status")
    public Map<String, Object> status(@AuthenticationPrincipal AuthPrincipal principal) {
        return status.status(principal.homeId());
    }

    @GetMapping("/{integration}/diagnostics")
    public IntegrationStatusService.Diagnostic diagnose(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable String integration) {
        return status.diagnose(principal.homeId(), integration);
    }

    /** Historial de la casa: descubrimientos, uniones Zigbee, conexiones y errores de todos los dispositivos. */
    @GetMapping("/events")
    public List<EventDto> events(@AuthenticationPrincipal AuthPrincipal principal,
                                 @RequestParam(defaultValue = "100") int limit) {
        return events.forHome(principal.homeId(), limit).stream()
                .map(e -> new EventDto(e.getId(), e.getDeviceId(), e.getEdgeId(), e.getType().name(), e.getDetails(), e.getCreatedAt()))
                .toList();
    }
}
