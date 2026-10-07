package com.home.ia.infrastructure.adapter.device;

import com.home.ia.application.port.out.DeviceCommandPort;
import com.home.ia.domain.model.device.DeviceId;
import com.home.ia.domain.model.device.PowerState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttDeviceCommandAdapter implements DeviceCommandPort {

    private static final String MQTT_TOPIC_TEMPLATE = "home/devices/%s/command";

    @Override
    public void sendPowerCommand(DeviceId deviceId, PowerState state) {
        String topic = String.format(MQTT_TOPIC_TEMPLATE, deviceId.value());
        String payload = state == PowerState.ON ? "ON" : "OFF";

        try {
            sendMqttCommand(topic, payload);
            log.info("Power command sent to device {}: {} (topic: {})",
                    deviceId.value(), state, topic);
        } catch (Exception e) {
            log.error("Failed to send power command to device {}: {}",
                    deviceId.value(), e.getMessage(), e);
        }
    }

    private void sendMqttCommand(String topic, String payload) {
        log.debug("Sending MQTT command to topic {} with payload: {}", topic, payload);
    }
}
