package com.home.ia.infrastructure.adapter.in.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttDeviceListener {

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handleDeviceMessage(Message<?> message) {
        try {
            String payload = (String) message.getPayload();
            String topic = message.getHeaders().get("mqtt_receivedTopic", String.class);

            log.debug("Received MQTT message from topic {}: {}", topic, payload);

            if (topic == null || !topic.startsWith("home/devices/")) {
                log.warn("Invalid MQTT topic: {}", topic);
                return;
            }

            processMqttMessage(topic, payload);
            log.info("Successfully processed MQTT message from {}", topic);
        } catch (Exception e) {
            log.error("Error processing MQTT message: {}", e.getMessage(), e);
        }
    }

    private void processMqttMessage(String topic, String payload) {
        String[] parts = topic.split("/");
        if (parts.length < 3) {
            log.warn("Invalid MQTT topic format: {}", topic);
            return;
        }

        String deviceId = parts[2];
        String messageType = parts.length > 3 ? parts[3] : "status";

        log.debug("Processing message from device {}: type={}, payload={}", deviceId, messageType, payload);

        switch (messageType) {
            case "status":
                handleDeviceStatus(deviceId, payload);
                break;
            case "energy":
                handleEnergyData(deviceId, payload);
                break;
            case "presence":
                handlePresenceData(deviceId, payload);
                break;
            default:
                log.warn("Unknown message type: {}", messageType);
        }
    }

    private void handleDeviceStatus(String deviceId, String payload) {
        log.info("Device {} status update: {}", deviceId, payload);
    }

    private void handleEnergyData(String deviceId, String payload) {
        try {
            double consumption = Double.parseDouble(payload);
            log.info("Device {} energy consumption: {} W", deviceId, consumption);
        } catch (NumberFormatException e) {
            log.error("Invalid energy data format from device {}: {}", deviceId, payload);
        }
    }

    private void handlePresenceData(String deviceId, String payload) {
        boolean present = "1".equals(payload) || "true".equalsIgnoreCase(payload);
        log.info("Device {} presence: {}", deviceId, present ? "occupied" : "vacant");
    }
}
