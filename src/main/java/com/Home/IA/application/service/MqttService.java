package com.home.ia.application.service;

import com.home.ia.infrastructure.mqtt.MqttAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttService {

    private final MqttAdapter mqttAdapter;
    private final AuditService auditService;

    public void toggleDevice(String userId, String deviceId, String deviceName) {
        try {
            if (!mqttAdapter.isConnected()) {
                log.warn("MQTT no está conectado, intentando reconectar...");
                mqttAdapter.connectToBroker();
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "TOGGLE");
            payload.put("timestamp", System.currentTimeMillis());

            mqttAdapter.publishDeviceCommand(deviceId, "TOGGLE", payload);
            log.info("Toggle command enviado a device: {}", deviceId);

            auditService.logDeviceAction(userId, deviceId, deviceName, "TOGGLE_VIA_MQTT",
                    "Comando toggle enviado via MQTT");
        } catch (Exception e) {
            log.error("Error al enviar toggle command: {}", e.getMessage(), e);
            auditService.logDeviceAction(userId, deviceId, deviceName, "TOGGLE_ERROR",
                    "Error: " + e.getMessage());
        }
    }

    public void turnOnDevice(String userId, String deviceId, String deviceName) {
        try {
            if (!mqttAdapter.isConnected()) {
                log.warn("MQTT no está conectado, intentando reconectar...");
                mqttAdapter.connectToBroker();
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "ON");
            payload.put("timestamp", System.currentTimeMillis());

            mqttAdapter.publishDeviceCommand(deviceId, "SET_STATE", payload);
            log.info("Turn ON command enviado a device: {}", deviceId);

            auditService.logDeviceAction(userId, deviceId, deviceName, "TURN_ON_VIA_MQTT",
                    "Comando turn-on enviado via MQTT");
        } catch (Exception e) {
            log.error("Error al enviar turn-on command: {}", e.getMessage(), e);
            auditService.logDeviceAction(userId, deviceId, deviceName, "TURN_ON_ERROR",
                    "Error: " + e.getMessage());
        }
    }

    public void turnOffDevice(String userId, String deviceId, String deviceName) {
        try {
            if (!mqttAdapter.isConnected()) {
                log.warn("MQTT no está conectado, intentando reconectar...");
                mqttAdapter.connectToBroker();
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "OFF");
            payload.put("timestamp", System.currentTimeMillis());

            mqttAdapter.publishDeviceCommand(deviceId, "SET_STATE", payload);
            log.info("Turn OFF command enviado a device: {}", deviceId);

            auditService.logDeviceAction(userId, deviceId, deviceName, "TURN_OFF_VIA_MQTT",
                    "Comando turn-off enviado via MQTT");
        } catch (Exception e) {
            log.error("Error al enviar turn-off command: {}", e.getMessage(), e);
            auditService.logDeviceAction(userId, deviceId, deviceName, "TURN_OFF_ERROR",
                    "Error: " + e.getMessage());
        }
    }

    public void requestDeviceStatus(String userId, String deviceId, String deviceName) {
        try {
            if (!mqttAdapter.isConnected()) {
                log.warn("MQTT no está conectado, intentando reconectar...");
                mqttAdapter.connectToBroker();
            }

            mqttAdapter.publishStatusRequest(deviceId);
            log.info("Status request enviado a device: {}", deviceId);

            auditService.logDeviceAction(userId, deviceId, deviceName, "STATUS_REQUEST_VIA_MQTT",
                    "Status request enviado via MQTT");
        } catch (Exception e) {
            log.error("Error al enviar status request: {}", e.getMessage(), e);
        }
    }

    public boolean isConnected() {
        return mqttAdapter.isConnected();
    }

    public void disconnect() {
        mqttAdapter.disconnect();
        log.info("MQTT desconectado");
    }
}
