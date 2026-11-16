package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.integration.Json;
import com.homeia.integration.MqttGateway;
import com.homeia.persistence.DeviceEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Envía órdenes a HomeIA Edge; la conexión HTTP con Shelly ocurre dentro de la LAN. */
@Component
public class ShellyDeviceDriver implements DeviceDriver {
    private final MqttGateway mqtt;
    public ShellyDeviceDriver(MqttGateway mqtt) { this.mqtt = mqtt; }
    @Override public DeviceProvider provider() { return DeviceProvider.SHELLY; }
    @Override public Optional<String> unavailableReason() {
        if (!mqtt.enabled()) return Optional.of("MQTT está deshabilitado; Shelly requiere HomeIA Edge en la LAN");
        if (!mqtt.connected()) return Optional.of("No hay conexión con el broker MQTT de HomeIA Edge");
        return Optional.empty();
    }
    @Override public Optional<Map<String,Object>> send(DeviceEntity d, DeviceCommand c, Integer brightness, String requestedBy) {
        if (c == DeviceCommand.BRIGHTNESS) throw new DeviceDriverException("El brillo Shelly depende del modelo; esta versión controla relés");
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("provider", "SHELLY"); body.put("address", d.getNetworkAddress());
        body.put("command", c.name()); body.put("deviceId", d.getId().toString());
        mqtt.publish("homeia/edge/command", Json.write(body));
        return Optional.empty();
    }
    @Override public Optional<Map<String,Object>> readState(DeviceEntity d) { return Optional.empty(); }
}
