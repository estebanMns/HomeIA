package com.home.ia.infrastructure.adapter.rest.controller;

import com.home.ia.application.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Controller
@Slf4j
public class NotificationController {

    private final WebSocketService webSocketService;

    public NotificationController(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @MessageMapping("/notification/subscribe")
    @SendTo("/topic/notifications")
    public Map<String, Object> handleSubscribe(SubscribeMessage message) {
        log.info("Cliente suscrito a notificaciones: {}", message.getClientId());

        Map<String, Object> response = new HashMap<>();
        response.put("type", "SUBSCRIPTION_CONFIRMED");
        response.put("clientId", message.getClientId());
        response.put("timestamp", Instant.now());
        response.put("message", "Suscripción exitosa");

        return response;
    }

    @MessageMapping("/device/subscribe")
    @SendTo("/topic/devices")
    public Map<String, Object> subscribeToDevice(DeviceSubscribeMessage message) {
        log.info("Cliente suscrito a dispositivo: {}", message.getDeviceId());

        Map<String, Object> response = new HashMap<>();
        response.put("type", "DEVICE_SUBSCRIPTION_CONFIRMED");
        response.put("deviceId", message.getDeviceId());
        response.put("timestamp", Instant.now());
        response.put("message", "Suscripción a dispositivo exitosa");

        return response;
    }

    @MessageMapping("/mqtt/subscribe")
    @SendTo("/topic/mqtt/status")
    public Map<String, Object> subscribeToMqtt(MqttSubscribeMessage message) {
        log.info("Cliente suscrito a MQTT: {}", message.getClientId());

        Map<String, Object> response = new HashMap<>();
        response.put("type", "MQTT_SUBSCRIPTION_CONFIRMED");
        response.put("clientId", message.getClientId());
        response.put("timestamp", Instant.now());
        response.put("message", "Suscripción a MQTT exitosa");

        return response;
    }

    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public Map<String, Object> handlePing() {
        log.debug("Ping recibido, enviando pong");

        Map<String, Object> response = new HashMap<>();
        response.put("type", "PONG");
        response.put("timestamp", Instant.now());

        return response;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SubscribeMessage {
        private String clientId;
        private String userId;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeviceSubscribeMessage {
        private String deviceId;
        private String clientId;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MqttSubscribeMessage {
        private String clientId;
        private String topic;
    }
}

@RestController
@RequestMapping("/api/ws")
@RequiredArgsConstructor
@Slf4j
class WebSocketStatusController {

    private final WebSocketService webSocketService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getWebSocketStatus() {
        log.debug("GET /api/ws/status - Verificando WebSocket status");

        Map<String, Object> response = new HashMap<>();
        response.put("connected", true);
        response.put("status", "WebSocket conectado");
        response.put("endpoints", new String[]{
            "/ws/notifications",
            "/ws/devices",
            "/ws/mqtt"
        });
        response.put("timestamp", Instant.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/test-notification")
    public ResponseEntity<Map<String, Object>> sendTestNotification() {
        log.info("GET /api/ws/test-notification - Enviando notificación de prueba");

        try {
            webSocketService.notifyDeviceStatusChanged(
                "device-test",
                "ONLINE",
                Map.of(
                    "temperature", 22.5,
                    "humidity", 65.0,
                    "lastUpdate", System.currentTimeMillis()
                )
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Notificación de prueba enviada");
            response.put("timestamp", Instant.now());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error enviando notificación de prueba: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error: " + e.getMessage());

            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/test-alert")
    public ResponseEntity<Map<String, Object>> sendTestAlert() {
        log.info("GET /api/ws/test-alert - Enviando alerta de prueba");

        try {
            webSocketService.notifyAlert(
                "alert-test-001",
                "WARNING",
                "Alerta de Prueba",
                "Esta es una alerta de prueba del sistema WebSocket"
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Alerta de prueba enviada");
            response.put("timestamp", Instant.now());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error enviando alerta de prueba: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error: " + e.getMessage());

            return ResponseEntity.badRequest().body(error);
        }
    }
}
