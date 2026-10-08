package com.home.ia.infrastructure.adapter.rest.controller;

import com.home.ia.application.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Slf4j
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getMetricsSummary() {
        log.info("GET /api/metrics/summary - Obteniendo resumen de métricas");

        Map<String, Object> response = new HashMap<>();
        response.put("activeDevices", metricsService.getActiveDevices());
        response.put("activeMqttConnections", metricsService.getActiveMqttConnections());
        response.put("activeWebSocketConnections", metricsService.getActiveWebSocketConnections());
        response.put("timestamp", Instant.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/devices")
    public ResponseEntity<Map<String, Object>> getDeviceMetrics() {
        log.debug("GET /api/metrics/devices - Métricas de dispositivos");

        Map<String, Object> response = new HashMap<>();
        response.put("activeDevices", metricsService.getActiveDevices());
        response.put("timestamp", Instant.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/mqtt")
    public ResponseEntity<Map<String, Object>> getMqttMetrics() {
        log.debug("GET /api/metrics/mqtt - Métricas de MQTT");

        Map<String, Object> response = new HashMap<>();
        response.put("activeMqttConnections", metricsService.getActiveMqttConnections());
        response.put("timestamp", Instant.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/websocket")
    public ResponseEntity<Map<String, Object>> getWebSocketMetrics() {
        log.debug("GET /api/metrics/websocket - Métricas de WebSocket");

        Map<String, Object> response = new HashMap<>();
        response.put("activeWebSocketConnections", metricsService.getActiveWebSocketConnections());
        response.put("timestamp", Instant.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getApplicationHealth() {
        log.info("GET /api/metrics/health - Health check de la aplicación");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("activeDevices", metricsService.getActiveDevices());
        response.put("activeMqttConnections", metricsService.getActiveMqttConnections());
        response.put("activeWebSocketConnections", metricsService.getActiveWebSocketConnections());
        response.put("timestamp", Instant.now());
        response.put("description", "Aplicación HomeIA operativa");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/prometheus-info")
    public ResponseEntity<Map<String, Object>> getPrometheusInfo() {
        log.debug("GET /api/metrics/prometheus-info - Información de Prometheus");

        Map<String, Object> response = new HashMap<>();
        response.put("prometheusEndpoint", "/actuator/prometheus");
        response.put("metricsEndpoints", new String[]{
            "/api/metrics/summary",
            "/api/metrics/devices",
            "/api/metrics/mqtt",
            "/api/metrics/websocket",
            "/api/metrics/health"
        });
        response.put("customMetrics", new String[]{
            "auth.login.success",
            "auth.login.failure",
            "device.commands.total",
            "mqtt.publish.total",
            "mqtt.subscribe.total",
            "alerts.total",
            "automation.actions.total",
            "websocket.messages.total",
            "devices.active",
            "mqtt.connections.active",
            "websocket.connections.active",
            "device.command.duration",
            "mqtt.command.duration",
            "auth.duration"
        });
        response.put("timestamp", Instant.now());

        return ResponseEntity.ok(response);
    }
}
