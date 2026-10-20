package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.integration.Json;
import com.homeia.integration.MqttGateway;
import com.homeia.persistence.DeviceEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Dispositivos propios (p. ej. ESP32) que hablan el protocolo MQTT de HomeIA. */
@Component
public class MqttDeviceDriver implements DeviceDriver {

    private final MqttGateway mqtt;

    public MqttDeviceDriver(MqttGateway mqtt) {
        this.mqtt = mqtt;
    }

    @Override
    public DeviceProvider provider() {
        return DeviceProvider.MQTT;
    }

    @Override
    public Optional<String> unavailableReason() {
        if (!mqtt.enabled()) return Optional.of("El broker MQTT no está habilitado (MQTT_ENABLED=false)");
        if (!mqtt.connected()) return Optional.of("El broker MQTT no está conectado");
        return Optional.empty();
    }

    @Override
    public Optional<Map<String, Object>> send(DeviceEntity device, DeviceCommand command, Integer brightness,
                                              String requestedBy) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("command", command.name());
        if (brightness != null) message.put("brightness", brightness);
        message.put("requestedAt", Instant.now().toString());
        message.put("requestedBy", requestedBy);
        try {
            mqtt.publishCommand(device.getId(), Json.write(message));
        } catch (IllegalStateException e) {
            throw new DeviceDriverException(e.getMessage(), e);
        }
        return Optional.empty(); // el estado llega cuando el dispositivo lo publica
    }

    @Override
    public Optional<Map<String, Object>> readState(DeviceEntity device) {
        return Optional.empty(); // el dispositivo publica su estado en {prefijo}/{id}/state
    }
}
