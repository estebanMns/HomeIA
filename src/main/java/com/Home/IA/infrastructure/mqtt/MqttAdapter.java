package com.home.ia.infrastructure.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttAdapter {

    private final MqttConfig.MqttProperties mqttProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MqttClient mqttClient;

    public void connectToBroker() {
        try {
            String clientId = mqttProperties.getClientId() + "-" + System.currentTimeMillis();
            mqttClient = new MqttClient(mqttProperties.getBrokerUrl(), clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            if (mqttProperties.getUsername() != null && !mqttProperties.getUsername().isEmpty()) {
                options.setUserName(mqttProperties.getUsername());
                options.setPassword(mqttProperties.getPassword().toCharArray());
            }

            mqttClient.connect(options);
            log.info("Conectado a MQTT Broker: {}", mqttProperties.getBrokerUrl());
        } catch (MqttException e) {
            log.error("Error conectando a MQTT broker: {}", e.getMessage(), e);
        }
    }

    public void publishDeviceCommand(String deviceId, String command, Map<String, Object> payload) {
        try {
            if (mqttClient == null || !mqttClient.isConnected()) {
                connectToBroker();
            }

            String topic = "devices/" + deviceId + "/command";

            Map<String, Object> message = new HashMap<>();
            message.put("deviceId", deviceId);
            message.put("command", command);
            message.put("payload", payload);
            message.put("timestamp", System.currentTimeMillis());

            String jsonPayload = objectMapper.writeValueAsString(message);
            mqttClient.publish(topic, jsonPayload.getBytes(), 1, false);

            log.info("Comando MQTT publicado - Device: {}, Command: {}, Topic: {}", deviceId, command, topic);
        } catch (Exception e) {
            log.error("Error publicando comando MQTT: {}", e.getMessage(), e);
        }
    }

    public void publishStatusRequest(String deviceId) {
        try {
            if (mqttClient == null || !mqttClient.isConnected()) {
                connectToBroker();
            }

            String topic = "devices/" + deviceId + "/request-status";
            String message = "{\"timestamp\": " + System.currentTimeMillis() + "}";

            mqttClient.publish(topic, message.getBytes(), 1, false);
            log.info("Status request MQTT publicado - Device: {}", deviceId);
        } catch (Exception e) {
            log.error("Error publicando status request: {}", e.getMessage(), e);
        }
    }

    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                log.info("Desconectado de MQTT broker");
            }
        } catch (MqttException e) {
            log.error("Error desconectando de MQTT: {}", e.getMessage(), e);
        }
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }
}
