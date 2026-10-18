package com.homeia.integration;

import com.homeia.config.HomeIaProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Puente con el broker MQTT de los dispositivos.
 * Comandos: {prefix}/{deviceId}/command  ·  Estado reportado: {prefix}/{deviceId}/state
 *
 * Reconexión:
 * - Con cleanSession=true el broker olvida las suscripciones al reconectar, así que la suscripción se hace
 *   en {@link #connectComplete(boolean, String)}, que Paho llama en la primera conexión y en cada reconexión.
 * - La reconexión automática de Paho solo actúa después de una primera conexión exitosa. Si el broker no
 *   estaba disponible al arrancar, {@link #retryIfNeverConnected()} lo vuelve a intentar periódicamente.
 */
@Slf4j
@Component
public class MqttGateway implements MqttCallbackExtended {

    private final HomeIaProperties.Mqtt properties;
    private final ApplicationEventPublisher events;
    /** La suscripción no se hace en el hilo de callbacks de Paho: una llamada bloqueante ahí puede trabarlo. */
    private final ExecutorService subscriber = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mqtt-subscriber");
        thread.setDaemon(true);
        return thread;
    });
    private volatile MqttClient client;

    public MqttGateway(HomeIaProperties properties, ApplicationEventPublisher events) {
        this.properties = properties.mqtt();
        this.events = events;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public boolean connected() {
        MqttClient current = client;
        return current != null && current.isConnected();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled()) {
            log.info("MQTT deshabilitado (MQTT_ENABLED=false)");
            return;
        }
        connect();
    }

    /** Reintenta solo si nunca hubo conexión; las caídas posteriores las resuelve la reconexión automática. */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void retryIfNeverConnected() {
        if (enabled() && client == null) {
            log.info("Reintentando la conexión con el broker MQTT {}", properties.brokerUrl());
            connect();
        }
    }

    synchronized void connect() {
        if (client != null) {
            return;
        }
        MqttClient candidate = null;
        try {
            candidate = new MqttClient(properties.brokerUrl(), properties.clientId(), new MemoryPersistence());
            candidate.setCallback(this);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            if (properties.username() != null && !properties.username().isBlank()) {
                options.setUserName(properties.username());
                options.setPassword(properties.password().toCharArray());
            }
            client = candidate; // antes de connect(): connectComplete puede llegar mientras connect() termina
            candidate.connect(options);
            log.info("Conectado al broker MQTT {}", properties.brokerUrl());
        } catch (Exception e) {
            log.error("No se pudo conectar al broker MQTT {}: {}. Se reintentará en 30 s",
                    properties.brokerUrl(), e.getMessage());
            client = null;
            closeQuietly(candidate);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        subscriber.execute(this::subscribeToStates);
    }

    private void subscribeToStates() {
        MqttClient current = client;
        if (current == null || !current.isConnected()) {
            return;
        }
        String topic = stateTopicFilter();
        try {
            current.subscribe(topic, 1);
            log.info("Suscrito a {}", topic);
        } catch (Exception e) {
            log.error("No se pudo suscribir a {}: {}", topic, e.getMessage());
        }
    }

    String stateTopicFilter() {
        return properties.topicPrefix() + "/+/state";
    }

    /** Publica un comando JSON para un dispositivo. Lanza IllegalStateException si MQTT no está disponible. */
    public void publishCommand(UUID deviceId, String json) {
        MqttClient current = client;
        if (current == null || !current.isConnected()) {
            throw new IllegalStateException("El broker MQTT no está conectado");
        }
        try {
            MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            current.publish(properties.topicPrefix() + "/" + deviceId + "/command", message);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo publicar el comando MQTT: " + e.getMessage(), e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String[] parts = topic.split("/");
            UUID deviceId = UUID.fromString(parts[parts.length - 2]);
            events.publishEvent(new DeviceReportEvent(deviceId, new String(message.getPayload(), StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("Mensaje MQTT ignorado en {}: {}", topic, e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Conexión MQTT perdida: {}. Paho intentará reconectar", cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Publicación confirmada por el broker
    }

    @PreDestroy
    public synchronized void disconnect() {
        subscriber.shutdownNow();
        MqttClient current = client;
        client = null;
        try {
            if (current != null && current.isConnected()) {
                current.disconnect();
            }
        } catch (Exception e) {
            log.warn("Error al cerrar MQTT: {}", e.getMessage());
        } finally {
            closeQuietly(current);
        }
    }

    private static void closeQuietly(MqttClient mqttClient) {
        if (mqttClient == null) {
            return;
        }
        try {
            mqttClient.close();
        } catch (Exception ignored) {
            // nada que hacer: el cliente ya no se usa
        }
    }
}
