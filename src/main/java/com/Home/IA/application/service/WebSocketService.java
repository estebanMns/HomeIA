package com.home.ia.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyDeviceStatusChanged(String deviceId, String status, Map<String, Object> details) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "DEVICE_STATUS_CHANGED");
            notification.put("deviceId", deviceId);
            notification.put("status", status);
            notification.put("details", details);
            notification.put("timestamp", Instant.now());

            String topicDevice = "/topic/devices/" + deviceId;
            String topicAll = "/topic/devices";
            messagingTemplate.convertAndSend(topicDevice, (Object) notification);
            messagingTemplate.convertAndSend(topicAll, (Object) notification);

            log.info("WebSocket notification enviado - Device: {}, Status: {}", deviceId, status);
        } catch (Exception e) {
            log.error("Error enviando WebSocket notification: {}", e.getMessage(), e);
        }
    }

    public void notifyMqttMessage(String topic, String payload, String deviceId) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "MQTT_MESSAGE_RECEIVED");
            notification.put("topic", topic);
            notification.put("payload", payload);
            notification.put("deviceId", deviceId);
            notification.put("timestamp", Instant.now());

            String mqttTopic = "/topic/mqtt/messages";
            messagingTemplate.convertAndSend(mqttTopic, (Object) notification);
            if (deviceId != null) {
                String deviceTopic = "/topic/devices/" + deviceId;
                messagingTemplate.convertAndSend(deviceTopic, (Object) notification);
            }

            log.debug("WebSocket MQTT notification enviado - Topic: {}", topic);
        } catch (Exception e) {
            log.error("Error enviando MQTT WebSocket notification: {}", e.getMessage(), e);
        }
    }

    public void notifyDeviceCommand(String deviceId, String command, String status, String message) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "DEVICE_COMMAND");
            notification.put("deviceId", deviceId);
            notification.put("command", command);
            notification.put("status", status);
            notification.put("message", message);
            notification.put("timestamp", Instant.now());

            String deviceTopic = "/topic/devices/" + deviceId;
            String commandTopic = "/topic/commands";
            messagingTemplate.convertAndSend(deviceTopic, (Object) notification);
            messagingTemplate.convertAndSend(commandTopic, (Object) notification);

            log.info("WebSocket command notification enviado - Device: {}, Command: {}", deviceId, command);
        } catch (Exception e) {
            log.error("Error enviando command WebSocket notification: {}", e.getMessage(), e);
        }
    }

    public void notifyAlert(String alertId, String severity, String title, String message) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "ALERT");
            notification.put("alertId", alertId);
            notification.put("severity", severity);
            notification.put("title", title);
            notification.put("message", message);
            notification.put("timestamp", Instant.now());

            String alertTopic = "/topic/alerts";
            String queueTopic = "/queue/alerts";
            messagingTemplate.convertAndSend(alertTopic, (Object) notification);
            messagingTemplate.convertAndSend(queueTopic, (Object) notification);

            log.info("WebSocket alert notification enviado - AlertID: {}, Severity: {}", alertId, severity);
        } catch (Exception e) {
            log.error("Error enviando alert WebSocket notification: {}", e.getMessage(), e);
        }
    }

    public void notifyAutomationEvent(String automationId, String eventType, String details) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "AUTOMATION_EVENT");
            notification.put("automationId", automationId);
            notification.put("eventType", eventType);
            notification.put("details", details);
            notification.put("timestamp", Instant.now());

            String automationTopic = "/topic/automation";
            messagingTemplate.convertAndSend(automationTopic, (Object) notification);

            log.debug("WebSocket automation notification enviado - Type: {}", eventType);
        } catch (Exception e) {
            log.error("Error enviando automation WebSocket notification: {}", e.getMessage(), e);
        }
    }

    public void notifyConnectionStatus(String status, String message) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "CONNECTION_STATUS");
            notification.put("status", status);
            notification.put("message", message);
            notification.put("timestamp", Instant.now());

            String statusTopic = "/topic/connection-status";
            messagingTemplate.convertAndSend(statusTopic, (Object) notification);

            log.info("WebSocket connection status enviado - Status: {}", status);
        } catch (Exception e) {
            log.error("Error enviando connection status WebSocket notification: {}", e.getMessage(), e);
        }
    }

    public void broadcastToAll(String channel, Map<String, Object> data) {
        try {
            data.put("timestamp", Instant.now());
            String topic = "/topic/" + channel;
            messagingTemplate.convertAndSend(topic, (Object) data);
            log.debug("WebSocket broadcast enviado a: /topic/{}", channel);
        } catch (Exception e) {
            log.error("Error broadcast WebSocket: {}", e.getMessage(), e);
        }
    }
}
