package com.home.ia.infrastructure.adapter.device;

import com.home.ia.application.port.out.DeviceCommandPort;
import com.home.ia.domain.model.device.DeviceId;
import com.home.ia.domain.model.device.PowerState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttDeviceCommandAdapter implements DeviceCommandPort {

    private static final String MQTT_TOPIC_TEMPLATE = "home/devices/%s/command";

    private final MessagingTemplate messagingTemplate;

    @Value("${mqtt.enabled:true}")
    private boolean mqttEnabled;

    @Override
    public void sendPowerCommand(DeviceId deviceId, PowerState state) {
        String topic = String.format(MQTT_TOPIC_TEMPLATE, deviceId.value());
        String payload = state == PowerState.ON ? "ON" : "OFF";

        try {
            if (!mqttEnabled) {
                log.warn("MQTT is disabled, command not sent to device {}", deviceId.value());
                return;
            }

            sendMqttCommand(topic, payload);
            log.info("Power command sent to device {}: {} (topic: {})",
                    deviceId.value(), state, topic);
        } catch (Exception e) {
            log.error("Failed to send power command to device {}: {}",
                    deviceId.value(), e.getMessage(), e);
            throw new RuntimeException("Failed to send MQTT command", e);
        }
    }

    private void sendMqttCommand(String topic, String payload) {
        try {
            messagingTemplate.convertAndSend("mqttOutboundChannel", payload);
            log.debug("MQTT message published to topic {} with payload: {}", topic, payload);
        } catch (Exception e) {
            log.error("Error publishing MQTT message to topic {}: {}", topic, e.getMessage());
            throw e;
        }
    }
}
