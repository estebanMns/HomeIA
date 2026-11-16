package com.homeia.integration;

import com.homeia.config.HomeIaProperties;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** MqttGateway contra un Mosquitto real: enrutado de tópicos de Edge y de dispositivos MQTT. Requiere Docker. */
@Testcontainers(disabledWithoutDocker = true)
class MqttGatewayIntegrationTest {

    @Container
    static final GenericContainer<?> MOSQUITTO = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0.20"))
            .withExposedPorts(1883)
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf");

    @Test
    void enrutaMensajesDeEdgeYEstadosDeDispositivos() throws Exception {
        String url = "tcp://" + MOSQUITTO.getHost() + ":" + MOSQUITTO.getMappedPort(1883);
        HomeIaProperties props = new HomeIaProperties(null, null,
                new HomeIaProperties.Mqtt(true, url, null, null, "homeia-test", "homeia/devices"),
                null, null, null, null, null, null, new HomeIaProperties.Edge("homeia/edge", 90, 120), null);
        List<Object> events = new CopyOnWriteArrayList<>();
        MqttGateway gateway = new MqttGateway(props, events::add);
        gateway.connect();
        try {
            waitFor(() -> events.stream().anyMatch(e -> e instanceof MqttConnectedEvent));
            MqttClient device = new MqttClient(url, "fake-edge", new MemoryPersistence());
            device.connect();
            device.publish("homeia/edge/11111111-2222-3333-4444-555555555555/heartbeat", "{\"x\":1}".getBytes(StandardCharsets.UTF_8), 1, false);
            device.publish("homeia/devices/11111111-2222-3333-4444-555555555555/state", "{\"powerState\":\"ON\"}".getBytes(StandardCharsets.UTF_8), 1, false);
            device.publish("homeia/edge/a/b/c", "{}".getBytes(StandardCharsets.UTF_8), 1, false);
            waitFor(() -> events.stream().anyMatch(e -> e instanceof DeviceReportEvent));
            waitFor(() -> events.stream().anyMatch(e -> e instanceof EdgeMessageEvent));
            EdgeMessageEvent edge = (EdgeMessageEvent) events.stream().filter(e -> e instanceof EdgeMessageEvent).findFirst().orElseThrow();
            assertEquals("heartbeat", edge.kind());
            assertEquals("11111111-2222-3333-4444-555555555555", edge.edgeId());
            device.disconnect();
            device.close();
        } finally {
            gateway.disconnect();
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("Tiempo de espera agotado");
            Thread.sleep(50);
        }
    }
}
