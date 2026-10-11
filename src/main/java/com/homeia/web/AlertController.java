package com.homeia.web;

import com.homeia.domain.AlertSeverity;
import com.homeia.domain.AlertType;
import com.homeia.persistence.AlertEntity;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.AlertService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    public record AlertDto(UUID id, AlertType type, AlertSeverity severity, String title, String message,
                           UUID roomId, UUID deviceId, boolean acknowledged, Instant acknowledgedAt,
                           String acknowledgedBy, Instant createdAt) {
        static AlertDto from(AlertEntity a) {
            return new AlertDto(a.getId(), a.getType(), a.getSeverity(), a.getTitle(), a.getMessage(), a.getRoomId(),
                    a.getDeviceId(), a.isAcknowledged(), a.getAcknowledgedAt(), a.getAcknowledgedBy(), a.getCreatedAt());
        }
    }

    public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T, S> PageDto<T> of(Page<S> page, java.util.function.Function<S, T> mapper) {
            return new PageDto<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    public record ManualAlertBody(@NotNull AlertSeverity severity, @NotBlank String title, @NotBlank String message,
                                  UUID roomId, UUID deviceId) {
    }

    @GetMapping
    public PageDto<AlertDto> list(@AuthenticationPrincipal AuthPrincipal principal,
                                  @RequestParam(required = false) AlertSeverity severity,
                                  @RequestParam(required = false) Boolean acknowledged,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return PageDto.of(alertService.search(principal.homeId(), severity, acknowledged, page, Math.min(size, 100)),
                AlertDto::from);
    }

    @GetMapping("/{id}")
    public AlertDto get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return AlertDto.from(alertService.get(principal.homeId(), id));
    }

    @PostMapping
    public AlertDto create(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ManualAlertBody body) {
        return AlertDto.from(alertService.createManual(principal, body.severity(), body.title(), body.message(),
                body.roomId(), body.deviceId()));
    }

    @PostMapping("/{id}/acknowledge")
    public AlertDto acknowledge(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return AlertDto.from(alertService.acknowledge(principal, id));
    }

    @PostMapping("/{id}/retry-notifications")
    public java.util.Map<String, Object> retry(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return java.util.Map.of("retried", alertService.retryNotifications(principal, id));
    }
}
