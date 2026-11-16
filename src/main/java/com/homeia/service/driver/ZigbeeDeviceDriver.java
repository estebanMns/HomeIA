package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.integration.Json;
import com.homeia.integration.MqttGateway;
import com.homeia.persistence.DeviceEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Adaptador Zigbee2MQTT. externalId almacena el friendly_name validado al incorporar el dispositivo. */
@Component
public class ZigbeeDeviceDriver implements DeviceDriver {
    private final MqttGateway mqtt;
    public ZigbeeDeviceDriver(MqttGateway mqtt) { this.mqtt = mqtt; }
    @Override public DeviceProvider provider() { return DeviceProvider.ZIGBEE; }
    @Override public Optional<String> unavailableReason() {
        if (!mqtt.enabled()) return Optional.of("MQTT está deshabilitado; Zigbee2MQTT necesita el broker local");
        if (!mqtt.connected()) return Optional.of("No hay conexión con el broker MQTT");
        return Optional.empty();
    }
    @Override public Optional<Map<String,Object>> send(DeviceEntity device, DeviceCommand command, Integer brightness, String requestedBy) {
        String name = device.getExternalId();
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,64}")) throw new DeviceDriverException("friendly_name Zigbee inválido");
        Map<String,Object> body = new LinkedHashMap<>();
        java.util.Set<String> capabilities = capabilities(device.getCapabilitiesJson());
        if (!capabilities.isEmpty() && (command == DeviceCommand.ON || command == DeviceCommand.OFF || command == DeviceCommand.TOGGLE)
                && !capabilities.contains("POWER"))
            throw new DeviceDriverException("Este dispositivo Zigbee no anuncia capacidad POWER");
        if (command == DeviceCommand.BRIGHTNESS && !capabilities.isEmpty() && !capabilities.contains("BRIGHTNESS"))
            throw new DeviceDriverException("Este dispositivo Zigbee no anuncia capacidad BRIGHTNESS");
        switch (command) {
            case ON -> body.put("state", "ON");
            case OFF -> body.put("state", "OFF");
            case TOGGLE -> body.put("state", "TOGGLE");
            case BRIGHTNESS -> body.put("brightness", brightness == null ? 0 : Math.round(brightness * 254f / 100f));
            case STATUS -> { return Optional.empty(); }
        }
        mqtt.publish("zigbee2mqtt/" + name + "/set", Json.write(body));
        return Optional.empty();
    }
    @Override public Optional<Map<String,Object>> readState(DeviceEntity device) { return Optional.empty(); }
    private static java.util.Set<String> capabilities(String json) {
        if (json == null || json.isBlank()) return java.util.Set.of();
        try {
            com.fasterxml.jackson.databind.JsonNode node = Json.read(json);
            if (!node.isArray()) return java.util.Set.of();
            java.util.Set<String> values = new java.util.HashSet<>();
            node.forEach(value -> { if (value.isTextual()) values.add(value.asText()); });
            return values;
        } catch (RuntimeException ignored) { return java.util.Set.of(); }
    }
}
