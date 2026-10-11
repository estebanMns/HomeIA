package com.homeia.web;

import com.homeia.persistence.AutomationActionEntity;
import com.homeia.persistence.AutomationSettingsEntity;
import com.homeia.domain.ActionType;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.ApiException;
import com.homeia.service.AutomationService;
import com.homeia.persistence.RoomEntity;
import com.homeia.persistence.RoomRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private final AutomationService automationService;
    private final RoomRepository roomRepository;

    public AutomationController(AutomationService automationService, RoomRepository roomRepository) {
        this.automationService = automationService;
        this.roomRepository = roomRepository;
    }

    public record SettingsDto(boolean enabled, int vacancyMinutes, int returnSensitivityPercent,
                              double consumptionAlertWatts, int leakMinutes, Instant updatedAt) {
        static SettingsDto from(AutomationSettingsEntity s) {
            return new SettingsDto(s.isEnabled(), s.getVacancyMinutes(), s.getReturnSensitivityPercent(),
                    s.getConsumptionAlertWatts(), s.getLeakMinutes(), s.getUpdatedAt());
        }
    }

    public record SettingsBody(
            @NotNull Boolean enabled,
            @Min(1) @Max(1440) int vacancyMinutes,
            @Min(1) @Max(100) int returnSensitivityPercent,
            @Min(1) double consumptionAlertWatts,
            @Min(1) @Max(1440) int leakMinutes) {
    }

    public record ActionDto(UUID id, UUID roomId, UUID deviceId, ActionType actionType, String description,
                            double wattsAvoided, Instant createdAt) {
        static ActionDto from(AutomationActionEntity a) {
            return new ActionDto(a.getId(), a.getRoomId(), a.getDeviceId(), a.getActionType(), a.getDescription(),
                    a.getWattsAvoided(), a.getCreatedAt());
        }
    }

    @GetMapping("/settings")
    public SettingsDto settings(@AuthenticationPrincipal AuthPrincipal principal) {
        return automationService.settings(principal.homeId())
                .map(SettingsDto::from)
                .orElseThrow(() -> ApiException.notFound("Configuración de automatización"));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsDto update(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody SettingsBody body) {
        return SettingsDto.from(automationService.updateSettings(principal, new AutomationService.Settings(
                body.enabled(), body.vacancyMinutes(), body.returnSensitivityPercent(),
                body.consumptionAlertWatts(), body.leakMinutes())));
    }

    @PostMapping("/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> enable(@AuthenticationPrincipal AuthPrincipal principal) {
        automationService.setEnabled(principal, true);
        return Map.of("enabled", true);
    }

    @PostMapping("/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> disable(@AuthenticationPrincipal AuthPrincipal principal) {
        automationService.setEnabled(principal, false);
        return Map.of("enabled", false);
    }

    @GetMapping("/status")
    public AutomationService.Status status(@AuthenticationPrincipal AuthPrincipal principal) {
        return automationService.status(principal.homeId());
    }

    @GetMapping("/actions")
    public Map<String, Object> actions(@AuthenticationPrincipal AuthPrincipal principal,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        var result = automationService.actions(principal.homeId(), page, Math.min(size, 100));
        List<ActionDto> content = result.getContent().stream().map(ActionDto::from).toList();
        return Map.of("content", content, "page", result.getNumber(), "size", result.getSize(),
                "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages());
    }

    /** Ejecuta un ciclo de automatización ahora mismo. */
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> run(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("actions", automationService.runNow(principal));
    }

    /** Probabilidad de que alguien regrese a la habitación (0-100). */
    @GetMapping("/rooms/{roomId}/return-probability")
    public Map<String, Object> returnProbability(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable UUID roomId) {
        RoomEntity room = roomRepository.findByIdAndHomeId(roomId, principal.homeId())
                .orElseThrow(() -> ApiException.notFound("Habitación"));
        return Map.of("roomId", roomId, "probabilityPercent", Math.round(automationService.returnProbabilityPercent(room)));
    }
}
