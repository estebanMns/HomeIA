package com.home.ia.infrastructure.adapter.rest.controller;

import com.home.ia.application.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/automation")
@RequiredArgsConstructor
@Slf4j
public class AutomationRestController {

    private final AuditService auditService;
    private static final String SYSTEM_USER = "SYSTEM";

    @GetMapping("/status")
    public ResponseEntity<AutomationStatus> getAutomationStatus() {
        log.info("GET /api/automation/status - Obteniendo estado de automatización");
        try {
            AutomationStatus status = AutomationStatus.builder()
                    .enabled(true)
                    .modelStatus("ACTIVE")
                    .modelAccuracy(94.2)
                    .lastDecisionTime(Instant.now())
                    .decisionCount(12)
                    .latencyMs(84.0)
                    .timestamp(Instant.now())
                    .build();

            log.info("Estado de automatización: enabled={}, accuracy={}", status.getEnabled(), status.getModelAccuracy());
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error al obtener estado de automatización: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/settings")
    public ResponseEntity<AutomationSettings> updateAutomationSettings(
            @RequestBody AutomationSettingsRequest request) {
        log.info("PUT /api/automation/settings - Actualizando configuración de automatización");
        try {
            log.debug("Nueva configuración: enabled={}, threshold={}, sensitivity={}",
                    request.getEnabled(), request.getVacantRoomWaitMinutes(), request.getReturnSensitivity());

            auditService.logAutomationAction(SYSTEM_USER, "global", "UPDATE_SETTINGS",
                    "Configuración actualizada: " + request.toString());

            AutomationSettings settings = AutomationSettings.builder()
                    .enabled(request.getEnabled())
                    .vacantRoomWaitMinutes(request.getVacantRoomWaitMinutes())
                    .returnSensitivity(request.getReturnSensitivity())
                    .consumptionThresholdWatts(1200)
                    .alertCooldownMinutes(30)
                    .quietHoursStart("22:00")
                    .quietHoursEnd("06:00")
                    .timestamp(Instant.now())
                    .build();

            log.info("Configuración de automatización actualizada");
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            log.error("Error al actualizar configuración de automatización: {}", e.getMessage(), e);
            auditService.logAutomationAction(SYSTEM_USER, "global", "UPDATE_SETTINGS_ERROR",
                    "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/enable")
    public ResponseEntity<Void> enableAutomation() {
        log.info("POST /api/automation/enable - Habilitando automatización");
        try {
            auditService.logAutomationAction(SYSTEM_USER, "global", "ENABLE",
                    "Automatización habilitada manualmente");
            log.info("Automatización habilitada");
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error al habilitar automatización: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disableAutomation() {
        log.info("POST /api/automation/disable - Deshabilitando automatización");
        try {
            auditService.logAutomationAction(SYSTEM_USER, "global", "DISABLE",
                    "Automatización deshabilitada manualmente");
            log.info("Automatización deshabilitada");
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error al deshabilitar automatización: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/actions")
    public ResponseEntity<ActionHistory> getAutomationActions(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("GET /api/automation/actions - Obteniendo historial de acciones (limit={})", limit);
        try {
            ActionHistory history = ActionHistory.builder()
                    .totalActions(47)
                    .successfulActions(45)
                    .failedActions(2)
                    .devicesControlled(8)
                    .energySavedKwh(36.1)
                    .estimatedSavingsCOP(32450.0)
                    .timestamp(Instant.now())
                    .build();

            log.info("Historial de acciones: {} acciones totales, {} exitosas",
                    history.getTotalActions(), history.getSuccessfulActions());
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error al obtener historial de acciones: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class AutomationStatus {
        private Boolean enabled;
        private String modelStatus;
        private Double modelAccuracy;
        private Instant lastDecisionTime;
        private Integer decisionCount;
        private Double latencyMs;
        private Instant timestamp;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class AutomationSettings {
        private Boolean enabled;
        private Integer vacantRoomWaitMinutes;
        private Integer returnSensitivity;
        private Integer consumptionThresholdWatts;
        private Integer alertCooldownMinutes;
        private String quietHoursStart;
        private String quietHoursEnd;
        private Instant timestamp;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class AutomationSettingsRequest {
        private Boolean enabled;
        private Integer vacantRoomWaitMinutes;
        private Integer returnSensitivity;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class ActionHistory {
        private Integer totalActions;
        private Integer successfulActions;
        private Integer failedActions;
        private Integer devicesControlled;
        private Double energySavedKwh;
        private Double estimatedSavingsCOP;
        private Instant timestamp;
    }
}
