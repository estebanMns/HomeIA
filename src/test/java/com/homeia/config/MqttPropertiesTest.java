package com.homeia.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MqttPropertiesTest {

    @Test
    void quitaEspaciosYComillasDelEnv() {
        HomeIaProperties.Mqtt mqtt = new HomeIaProperties.Mqtt(false, "tcp://localhost:1883   ", null, null,
                " \"homeia-backend\" ", "homeia ");
        assertEquals("tcp://localhost:1883", mqtt.brokerUrl());
        assertEquals("homeia-backend", mqtt.clientId());
        assertEquals("homeia", mqtt.topicPrefix());
        assertEquals(null, mqtt.username());
    }
}
