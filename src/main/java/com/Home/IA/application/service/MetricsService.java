package com.home.ia.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;

    private final AtomicInteger activeDevices = new AtomicInteger(0);
    private final AtomicInteger activeMqttConnections = new AtomicInteger(0);
    private final AtomicInteger activeWebSocketConnections = new AtomicInteger(0);

    // Counters
    private Counter loginSuccessCounter;
    private Counter loginFailureCounter;
    private Counter deviceCommandCounter;
    private Counter mqttPublishCounter;
    private Counter mqttSubscribeCounter;
    private Counter alertsCounter;
    private Counter automationActionsCounter;
    private Counter webSocketMessagesCounter;

    // Timers
    private Timer deviceCommandTimer;
    private Timer mqttCommandTimer;
    private Timer authenticationTimer;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }

    private void initializeMetrics() {
        log.info("Inicializando métricas de Prometheus");

        // Counters - Autenticación
        loginSuccessCounter = Counter.builder("auth.login.success")
                .description("Número de logins exitosos")
                .register(meterRegistry);

        loginFailureCounter = Counter.builder("auth.login.failure")
                .description("Número de intentos de login fallidos")
                .register(meterRegistry);

        // Counters - Dispositivos
        deviceCommandCounter = Counter.builder("device.commands.total")
                .description("Total de comandos enviados a dispositivos")
                .register(meterRegistry);

        // Counters - MQTT
        mqttPublishCounter = Counter.builder("mqtt.publish.total")
                .description("Total de mensajes publicados en MQTT")
                .register(meterRegistry);

        mqttSubscribeCounter = Counter.builder("mqtt.subscribe.total")
                .description("Total de suscripciones a MQTT")
                .register(meterRegistry);

        // Counters - Alertas
        alertsCounter = Counter.builder("alerts.total")
                .description("Total de alertas generadas")
                .register(meterRegistry);

        // Counters - Automatización
        automationActionsCounter = Counter.builder("automation.actions.total")
                .description("Total de acciones de automatización ejecutadas")
                .register(meterRegistry);

        // Counters - WebSocket
        webSocketMessagesCounter = Counter.builder("websocket.messages.total")
                .description("Total de mensajes enviados via WebSocket")
                .register(meterRegistry);

        // Timers
        deviceCommandTimer = Timer.builder("device.command.duration")
                .description("Duración de comandos de dispositivos en ms")
                .register(meterRegistry);

        mqttCommandTimer = Timer.builder("mqtt.command.duration")
                .description("Duración de comandos MQTT en ms")
                .register(meterRegistry);

        authenticationTimer = Timer.builder("auth.duration")
                .description("Duración del proceso de autenticación en ms")
                .register(meterRegistry);

        // Gauges - Conexiones activas
        Gauge.builder("devices.active", activeDevices::get)
                .description("Número de dispositivos activos")
                .register(meterRegistry);

        Gauge.builder("mqtt.connections.active", activeMqttConnections::get)
                .description("Número de conexiones MQTT activas")
                .register(meterRegistry);

        Gauge.builder("websocket.connections.active", activeWebSocketConnections::get)
                .description("Número de conexiones WebSocket activas")
                .register(meterRegistry);

        log.info("Métricas de Prometheus inicializadas exitosamente");
    }

    // Métodos de registro de métricas

    public void recordLoginSuccess() {
        loginSuccessCounter.increment();
        log.debug("Login exitoso registrado");
    }

    public void recordLoginFailure() {
        loginFailureCounter.increment();
        log.debug("Login fallido registrado");
    }

    public void recordDeviceCommand() {
        deviceCommandCounter.increment();
    }

    public void recordDeviceCommandDuration(long durationMs) {
        deviceCommandTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordMqttPublish() {
        mqttPublishCounter.increment();
    }

    public void recordMqttCommandDuration(long durationMs) {
        mqttCommandTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordMqttSubscribe() {
        mqttSubscribeCounter.increment();
    }

    public void recordAlert() {
        alertsCounter.increment();
    }

    public void recordAutomationAction() {
        automationActionsCounter.increment();
    }

    public void recordWebSocketMessage() {
        webSocketMessagesCounter.increment();
    }

    public void recordAuthenticationDuration(long durationMs) {
        authenticationTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    // Gestión de conexiones activas

    public void incrementActiveDevices() {
        activeDevices.incrementAndGet();
        log.debug("Dispositivos activos: {}", activeDevices.get());
    }

    public void decrementActiveDevices() {
        activeDevices.decrementAndGet();
        log.debug("Dispositivos activos: {}", activeDevices.get());
    }

    public void setActiveDevices(int count) {
        activeDevices.set(count);
    }

    public int getActiveDevices() {
        return activeDevices.get();
    }

    public void incrementMqttConnections() {
        activeMqttConnections.incrementAndGet();
        log.info("Conexiones MQTT activas: {}", activeMqttConnections.get());
    }

    public void decrementMqttConnections() {
        activeMqttConnections.decrementAndGet();
        log.info("Conexiones MQTT activas: {}", activeMqttConnections.get());
    }

    public int getActiveMqttConnections() {
        return activeMqttConnections.get();
    }

    public void incrementWebSocketConnections() {
        activeWebSocketConnections.incrementAndGet();
        log.debug("Conexiones WebSocket activas: {}", activeWebSocketConnections.get());
    }

    public void decrementWebSocketConnections() {
        activeWebSocketConnections.decrementAndGet();
        log.debug("Conexiones WebSocket activas: {}", activeWebSocketConnections.get());
    }

    public int getActiveWebSocketConnections() {
        return activeWebSocketConnections.get();
    }

    // Exportar métricas

    public Map<String, Object> getMetricsSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("activeDevices", activeDevices.get());
        summary.put("activeMqttConnections", activeMqttConnections.get());
        summary.put("activeWebSocketConnections", activeWebSocketConnections.get());
        summary.put("timestamp", System.currentTimeMillis());
        return summary;
    }
}
