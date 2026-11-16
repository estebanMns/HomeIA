package com.homeia.web;

import com.homeia.domain.EdgeStatus;
import com.homeia.edge.EdgeInboundService;
import com.homeia.edge.EdgeRegistryService;
import com.homeia.integration.Json;
import com.homeia.persistence.EdgeNodeEntity;
import com.homeia.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HomeIA Edge de la casa: enrolar (devuelve el secreto una sola vez), rotar, revocar y eliminar.
 * Los secretos nunca se vuelven a mostrar: si se pierden, se rota.
 */
@RestController
@RequestMapping("/api/edges")
public class EdgeController {

    private final EdgeRegistryService registry;
    private final EdgeInboundService inbound;

    public EdgeController(EdgeRegistryService registry, EdgeInboundService inbound) {
        this.registry = registry;
        this.inbound = inbound;
    }

    public record EdgeDto(UUID id, String name, EdgeStatus status, String version, String platform,
                          String zigbeeBridgeState, boolean zigbeePermitJoin, Instant zigbeePermitJoinUntil,
                          int queueDepth, Map<String, Object> metrics, Instant lastHeartbeatAt, Instant lastHelloAt,
                          Instant rotatedAt, Instant previousSecretValidUntil, Instant revokedAt, Instant createdAt) {
        static EdgeDto from(EdgeNodeEntity e) {
            return new EdgeDto(e.getId(), e.getName(), e.getStatus(), e.getVersion(), e.getPlatform(),
                    e.getZigbeeBridgeState(), e.isZigbeePermitJoin(), e.getZigbeePermitJoinUntil(), e.getQueueDepth(),
                    Json.toMap(e.getMetricsJson()), e.getLastHeartbeatAt(),
                    e.getLastHelloAt(), e.getRotatedAt(), e.getPreviousValidUntil(), e.getRevokedAt(), e.getCreatedAt());
        }
    }

    /** Respuesta de enrolamiento/rotación: el secreto solo aparece aquí. */
    public record EnrollmentDto(EdgeDto edge, String secret, Map<String, String> env) {
    }

    public record EnrollBody(@NotBlank @Size(max = 120) String name) {
    }

    @GetMapping
    public List<EdgeDto> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return registry.list(principal.homeId()).stream().map(EdgeDto::from).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public EnrollmentDto enroll(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody EnrollBody body) {
        var e = registry.enroll(principal, body.name());
        return new EnrollmentDto(EdgeDto.from(e.edge()), e.secret(), e.env());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/rotate")
    public EnrollmentDto rotate(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        var e = registry.rotate(principal, id);
        return new EnrollmentDto(EdgeDto.from(e.edge()), e.secret(), e.env());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/revoke")
    public EdgeDto revoke(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return EdgeDto.from(registry.revoke(principal, id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/sync")
    public Map<String, Object> sync(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        inbound.resync(principal.homeId(), id);
        return Map.of("accepted", true);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        registry.delete(principal, id);
    }
}
