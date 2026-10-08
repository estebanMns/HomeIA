package com.home.ia.infrastructure.adapter.rest.controller;

import com.home.ia.application.service.MqttService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/devices/mqtt")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class DeviceCommandController {

    private final MqttService mqttService;

    @PostMapping("/{deviceId}/toggle")
    public ResponseEntity<CommandResponse> toggleDevice(
            @PathVariable String deviceId,
            Authentication authentication) {
        log.info("POST /api/devices/mqtt/{}/toggle - Toggling device via MQTT", deviceId);
        try {
            String userId = (String) authentication.getPrincipal();
            String deviceName = "Device-" + deviceId;

            mqttService.toggleDevice(userId, deviceId, deviceName);

            return ResponseEntity.accepted()
                    .body(CommandResponse.builder()
                            .success(true)
                            .message("Comando TOGGLE enviado al dispositivo")
                            .deviceId(deviceId)
                            .command("TOGGLE")
                            .timestamp(Instant.now())
                            .build());
        } catch (Exception e) {
            log.error("Error toggling device {}: {}", deviceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CommandResponse.builder()
                            .success(false)
                            .message("Error al enviar comando")
                            .deviceId(deviceId)
                            .build());
        }
    }

    @PostMapping("/{deviceId}/on")
    public ResponseEntity<CommandResponse> turnOnDevice(
            @PathVariable String deviceId,
            Authentication authentication) {
        log.info("POST /api/devices/mqtt/{}/on - Turning on device via MQTT", deviceId);
        try {
            String userId = (String) authentication.getPrincipal();
            String deviceName = "Device-" + deviceId;

            mqttService.turnOnDevice(userId, deviceId, deviceName);

            return ResponseEntity.accepted()
                    .body(CommandResponse.builder()
                            .success(true)
                            .message("Comando TURN_ON enviado al dispositivo")
                            .deviceId(deviceId)
                            .command("TURN_ON")
                            .timestamp(Instant.now())
                            .build());
        } catch (Exception e) {
            log.error("Error turning on device {}: {}", deviceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CommandResponse.builder()
                            .success(false)
                            .message("Error al enviar comando")
                            .deviceId(deviceId)
                            .build());
        }
    }

    @PostMapping("/{deviceId}/off")
    public ResponseEntity<CommandResponse> turnOffDevice(
            @PathVariable String deviceId,
            Authentication authentication) {
        log.info("POST /api/devices/mqtt/{}/off - Turning off device via MQTT", deviceId);
        try {
            String userId = (String) authentication.getPrincipal();
            String deviceName = "Device-" + deviceId;

            mqttService.turnOffDevice(userId, deviceId, deviceName);

            return ResponseEntity.accepted()
                    .body(CommandResponse.builder()
                            .success(true)
                            .message("Comando TURN_OFF enviado al dispositivo")
                            .deviceId(deviceId)
                            .command("TURN_OFF")
                            .timestamp(Instant.now())
                            .build());
        } catch (Exception e) {
            log.error("Error turning off device {}: {}", deviceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CommandResponse.builder()
                            .success(false)
                            .message("Error al enviar comando")
                            .deviceId(deviceId)
                            .build());
        }
    }

    @GetMapping("/{deviceId}/status")
    public ResponseEntity<CommandResponse> requestStatus(
            @PathVariable String deviceId,
            Authentication authentication) {
        log.info("GET /api/devices/mqtt/{}/status - Requesting status via MQTT", deviceId);
        try {
            String userId = (String) authentication.getPrincipal();
            String deviceName = "Device-" + deviceId;

            mqttService.requestDeviceStatus(userId, deviceId, deviceName);

            return ResponseEntity.accepted()
                    .body(CommandResponse.builder()
                            .success(true)
                            .message("Status request enviado al dispositivo")
                            .deviceId(deviceId)
                            .command("STATUS_REQUEST")
                            .timestamp(Instant.now())
                            .build());
        } catch (Exception e) {
            log.error("Error requesting status for device {}: {}", deviceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CommandResponse.builder()
                            .success(false)
                            .message("Error al solicitar status")
                            .deviceId(deviceId)
                            .build());
        }
    }

    @GetMapping("/mqtt/status")
    public ResponseEntity<MqttStatusResponse> getMqttStatus() {
        log.debug("GET /api/devices/mqtt/mqtt/status - Checking MQTT connection");
        return ResponseEntity.ok(MqttStatusResponse.builder()
                .connected(mqttService.isConnected())
                .message(mqttService.isConnected() ? "MQTT conectado" : "MQTT desconectado")
                .timestamp(Instant.now())
                .build());
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class CommandResponse {
        private Boolean success;
        private String message;
        private String deviceId;
        private String command;
        private Instant timestamp;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class MqttStatusResponse {
        private Boolean connected;
        private String message;
        private Instant timestamp;
    }
}
