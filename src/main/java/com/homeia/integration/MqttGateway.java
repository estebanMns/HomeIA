package com.homeia.integration;

import com.homeia.config.HomeIaProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Puente con el broker MQTT de los dispositivos.
 * Comandos: {prefix}/{deviceId}/command  ·  Estado reportado: {prefix}/{deviceId}/state
 */
@Slf4j
@Component
public class MqttGateway implements MqttCallback {

    private final HomeIaProperties.Mqtt properties;
    private final ApplicationEventPublisher events;
    private MqttClient client;

    public MqttGateway(HomeIaProperties properties, ApplicationEventPublisher events) {
        this.properties = properties.mqtt();
        this.events = events;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public synchronized boolean connected() {
        return client != null && client.isConnected();
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void connect() {
        if (!enabled()) {
            log.info("MQTT deshabilitado (MQTT_ENABLED=false)");
            return;
        }
        try {
            client = new MqttClient(properties.brokerUrl(), properties.clientId(), new MemoryPersistence());
            client.setCallback(this);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            if (properties.username() != null && !properties.username().isBlank()) {
                options.setUserName(properties.username());
                options.setPassword(properties.password().toCharArray());
            }
            client.connect(options);
            client.subscribe(properties.topicPrefix() + "/+/state", 1);
            log.info("Conectado al broker MQTT {} y suscrito a {}/+/state", properties.brokerUrl(), properties.topicPrefix());
        } catch (Exception e) {
            log.error("No se pudo conectar al broker MQTT: {}", e.getMessage());
        }
    }

    /** Publica un comando JSON para un dispositivo. Lanza IllegalStateException si MQTT no está disponible. */
    public synchronized void publishCommand(UUID deviceId, String json) {
        if (!connected()) {
            throw new IllegalStateException("El broker MQTT no está conectado");
        }
        try {
            MqttMessage message = new MqttMessage(json.getBytes());
            message.setQos(1);
            client.publish(properties.topicPrefix() + "/" + deviceId + "/command", message);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo publicar el comando MQTT: " + e.getMessage(), e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String[] parts = topic.split("/");
            UUID deviceId = UUID.fromString(parts[parts.length - 2]);
            events.publishEvent(new DeviceReportEvent(deviceId, new String(message.getPayload())));
        } catch (Exception e) {
            log.warn("Mensaje MQTT ignorado en {}: {}", topic, e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Conexión MQTT perdida: {}", cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Publicación confirmada por el broker
    }

    @PreDestroy
    public synchronized void disconnect() {
        try {
            if (connected()) {
                client.disconnect();
                client.close();
            }
        } catch (Exception e) {
            log.warn("Error al cerrar MQTT: {}", e.getMessage());
        }
    }
}
