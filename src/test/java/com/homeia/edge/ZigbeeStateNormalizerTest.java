package com.homeia.edge;

import com.homeia.integration.Json;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ZigbeeStateNormalizerTest {

    @Test
    void normalizaLuzYEnchufe() {
        Map<String, Object> r = ZigbeeStateNormalizer.normalize(Json.read(
                "{\"state\":\"on\",\"brightness\":254,\"power\":11.5,\"energy\":2.25,\"voltage\":120,\"linkquality\":90}"));
        assertEquals("ON", r.get("powerState"));
        assertEquals(100L, r.get("brightness"));
        assertEquals(11.5, r.get("consumptionWatts"));
        @SuppressWarnings("unchecked") Map<String, Object> t = (Map<String, Object>) r.get("telemetry");
        assertEquals(2.25, t.get("energyKwh"));
        assertEquals(120.0, t.get("voltage"));
    }

    @Test
    void normalizaSensores() {
        Map<String, Object> r = ZigbeeStateNormalizer.normalize(Json.read(
                "{\"temperature\":22.4,\"humidity\":55,\"battery\":87,\"contact\":false,\"occupancy\":true,\"illuminance_lux\":30}"));
        assertEquals(true, r.get("presence"));
        assertFalse(r.containsKey("powerState"));
        @SuppressWarnings("unchecked") Map<String, Object> t = (Map<String, Object>) r.get("telemetry");
        assertEquals(22.4, t.get("temperatureC"));
        assertEquals(55.0, t.get("humidityPercent"));
        assertEquals(87.0, t.get("batteryPercent"));
        assertEquals(true, t.get("contactOpen"));
        assertEquals(30.0, t.get("illuminanceLux"));
    }

    @Test
    void ignoraTiposInesperados() {
        Map<String, Object> r = ZigbeeStateNormalizer.normalize(Json.read("{\"state\":\"BLINK\",\"power\":\"x\",\"temperature\":null}"));
        assertTrue(r.isEmpty());
    }
}
