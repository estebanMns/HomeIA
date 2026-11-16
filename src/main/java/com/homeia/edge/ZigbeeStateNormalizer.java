package com.homeia.edge;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convierte el estado que publica Zigbee2MQTT ({"state":"ON","brightness":200,"temperature":22.4,…}) al
 * formato de reporte de HomeIA: powerState, brightness (0-100), consumptionWatts, presence y un mapa
 * "telemetry" con los sensores (temperatura, humedad, contacto, batería, energía, tensión, corriente, luz).
 * Los valores con tipo inesperado se ignoran en lugar de romper el reporte.
 */
public final class ZigbeeStateNormalizer {

    private ZigbeeStateNormalizer() {
    }

    public static Map<String, Object> normalize(JsonNode raw) {
        Map<String, Object> report = new LinkedHashMap<>();
        Map<String, Object> telemetry = new LinkedHashMap<>();
        if (raw == null || !raw.isObject()) return report;

        JsonNode state = first(raw, "state", "state_l1");
        if (state != null && state.isTextual()) {
            String s = state.asText();
            if ("ON".equalsIgnoreCase(s) || "OFF".equalsIgnoreCase(s)) report.put("powerState", s.toUpperCase());
        }
        JsonNode brightness = raw.get("brightness");
        if (brightness != null && brightness.isNumber()) {
            report.put("brightness", clamp(Math.round(brightness.asDouble() * 100 / 254.0), 0, 100));
        }
        JsonNode power = raw.get("power");
        if (power != null && power.isNumber() && power.asDouble() >= 0) report.put("consumptionWatts", power.asDouble());

        JsonNode occupancy = first(raw, "occupancy", "presence");
        if (occupancy != null && occupancy.isBoolean()) report.put("presence", occupancy.asBoolean());

        number(raw, "temperature", telemetry, "temperatureC");
        number(raw, "humidity", telemetry, "humidityPercent");
        number(raw, "battery", telemetry, "batteryPercent");
        number(raw, "energy", telemetry, "energyKwh");
        number(raw, "voltage", telemetry, "voltage");
        number(raw, "current", telemetry, "currentA");
        number(raw, "linkquality", telemetry, "linkQuality");
        JsonNode lux = first(raw, "illuminance_lux", "illuminance");
        if (lux != null && lux.isNumber()) telemetry.put("illuminanceLux", lux.asDouble());
        JsonNode contact = raw.get("contact");
        // Zigbee2MQTT: contact=true significa cerrado (los imanes se tocan).
        if (contact != null && contact.isBoolean()) telemetry.put("contactOpen", !contact.asBoolean());
        JsonNode batteryLow = raw.get("battery_low");
        if (batteryLow != null && batteryLow.isBoolean()) telemetry.put("batteryLow", batteryLow.asBoolean());
        if (occupancy != null && occupancy.isBoolean()) telemetry.put("occupancy", occupancy.asBoolean());

        if (!telemetry.isEmpty()) report.put("telemetry", telemetry);
        return report;
    }

    private static JsonNode first(JsonNode raw, String... names) {
        for (String name : names) {
            JsonNode value = raw.get(name);
            if (value != null && !value.isNull()) return value;
        }
        return null;
    }

    private static void number(JsonNode raw, String field, Map<String, Object> target, String key) {
        JsonNode value = raw.get(field);
        if (value != null && value.isNumber()) target.put(key, value.asDouble());
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
