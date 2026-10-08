package com.home.ia.infrastructure.adapter.rest.controller;

import com.home.ia.application.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Slf4j
public class AlertController {

    private final AuditService auditService;
    private static final String SYSTEM_USER = "SYSTEM";

    @GetMapping
    public ResponseEntity<List<AlertSummary>> getAllAlerts(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status) {
        log.info("GET /api/alerts - Obteniendo alertas (severity={}, status={})", severity, status);
        try {
            List<AlertSummary> summaries = new ArrayList<>();
            summaries.add(AlertSummary.builder()
                    .id("alert-1")
                    .severity("CRITICAL")
                    .title("Posible fuga de energía")
                    .deviceName("Calentador baño")
                    .message("Consumo de 1.500 W durante 47 min sin presencia")
                    .timestamp(Instant.now())
                    .whatsappStatus("ENVIADA")
                    .build());

            log.debug("Se encontraron {} alertas", summaries.size());
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            log.error("Error al obtener alertas: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertDetail> getAlert(@PathVariable String id) {
        log.info("GET /api/alerts/{} - Obteniendo detalle de alerta", id);
        try {
            AlertDetail detail = AlertDetail.builder()
                    .id(id)
                    .severity("CRITICAL")
                    .title("Posible fuga de energía")
                    .deviceName("Calentador baño")
                    .message("Consumo de 1.500 W durante 47 min sin presencia detectada")
                    .anomalyScore(92.0)
                    .whatsappStatus("ENVIADA")
                    .attempts(1)
                    .timestamp(Instant.now())
                    .build();

            log.debug("Alerta {} recuperada", id);
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            log.error("Error al obtener alerta {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Void> acknowledgeAlert(@PathVariable String id) {
        log.info("POST /api/alerts/{}/acknowledge - Marcando alerta como revisada", id);
        try {
            log.debug("Alerta {} marcada como revisada", id);
            auditService.logAlertAction(SYSTEM_USER, id, "ACKNOWLEDGE", "Alerta marcada como revisada");
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error al marcar alerta {} como revisada: {}", id, e.getMessage(), e);
            auditService.logAlertAction(SYSTEM_USER, id, "ACKNOWLEDGE_ERROR", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/retry-whatsapp")
    public ResponseEntity<Void> retryWhatsappNotification(@PathVariable String id) {
        log.info("POST /api/alerts/{}/retry-whatsapp - Reintentando notificación WhatsApp", id);
        try {
            log.debug("Reintentando envío de WhatsApp para alerta {}", id);
            auditService.logAlertAction(SYSTEM_USER, id, "RETRY_WHATSAPP", "Reintento de envío de notificación");
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error al reintentar WhatsApp para alerta {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class AlertSummary {
        private String id;
        private String severity;
        private String title;
        private String deviceName;
        private String message;
        private Instant timestamp;
        private String whatsappStatus;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class AlertDetail {
        private String id;
        private String severity;
        private String title;
        private String deviceName;
        private String message;
        private Double anomalyScore;
        private String whatsappStatus;
        private Integer attempts;
        private Instant timestamp;
    }
}
