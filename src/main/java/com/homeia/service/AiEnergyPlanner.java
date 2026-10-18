package com.homeia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.config.HomeIaProperties;
import com.homeia.integration.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decide con Gemini qué dispositivos encender, apagar o dejar como están.
 * La IA solo propone; {@link AutomationService} valida cada decisión contra el estado real antes de enviarla.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiEnergyPlanner {

    private static final Set<String> ACTIONS = Set.of("ON", "OFF", "KEEP");
    private static final int MAX_REASON_LENGTH = 300;

    private static final String INSTRUCTIONS = """
            Eres el controlador de energía de un hogar. Recibirás el estado actual de cada dispositivo,
            de cada habitación (si está ocupada y cuántos minutos lleva sin presencia) y el consumo de la última hora.
            Para CADA dispositivo decide una acción:
            - "OFF": está encendido, no es crítico, su habitación está vacía y no hay uso previsto.
            - "ON": está apagado y su habitación está ocupada.
            - "KEEP": en cualquier otro caso. Los dispositivos críticos siempre son "KEEP".
            Cuida el confort: ante la duda, usa "KEEP". Ten en cuenta la hora local y el consumo.
            Responde SOLO con JSON con esta forma exacta:
            {"decisions":[{"deviceId":"<id>","action":"ON|OFF|KEEP","reason":"<motivo breve en español>"}]}
            Incluye una decisión por cada dispositivo recibido.

            Estado de la casa:
            """;

    private final GeminiClient geminiClient;
    private final HomeIaProperties properties;
    private final Map<UUID, Instant> lastPlanAt = new ConcurrentHashMap<>();

    public record Decision(UUID deviceId, String action, String reason) {
    }

    public boolean enabled() {
        return geminiClient.configured();
    }

    /** True si ya pasó el intervalo configurado desde el último plan de esta casa. */
    public boolean due(UUID homeId, Instant now) {
        Instant last = lastPlanAt.get(homeId);
        return last == null || last.plusSeconds(properties.gemini().decisionIntervalSeconds()).isBefore(now);
    }

    /**
     * Pide el plan a Gemini. Devuelve vacío si la IA no respondió o la respuesta no sirve,
     * para que el motor use las reglas de respaldo en ese ciclo.
     */
    public Optional<List<Decision>> plan(UUID homeId, String snapshotJson, Set<UUID> knownDeviceIds) {
        lastPlanAt.put(homeId, Instant.now());
        try {
            return geminiClient.generateJson(INSTRUCTIONS + snapshotJson).map(root -> parse(root, knownDeviceIds));
        } catch (Exception e) {
            log.warn("Gemini no pudo planificar la casa {}: {}", homeId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Descarta entradas mal formadas, acciones desconocidas o dispositivos que no están en la casa. */
    static List<Decision> parse(JsonNode root, Set<UUID> knownDeviceIds) {
        List<Decision> decisions = new ArrayList<>();
        for (JsonNode item : root.path("decisions")) {
            String action = item.path("action").asText("").trim().toUpperCase(Locale.ROOT);
            if (!ACTIONS.contains(action)) {
                continue;
            }
            UUID deviceId;
            try {
                deviceId = UUID.fromString(item.path("deviceId").asText(""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!knownDeviceIds.contains(deviceId)) {
                continue;
            }
            String reason = item.path("reason").asText("sin motivo").trim();
            if (reason.length() > MAX_REASON_LENGTH) {
                reason = reason.substring(0, MAX_REASON_LENGTH);
            }
            decisions.add(new Decision(deviceId, action, reason));
        }
        return decisions;
    }
}
