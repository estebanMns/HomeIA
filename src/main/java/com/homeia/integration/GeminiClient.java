package com.homeia.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.config.HomeIaProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Cliente de Gemini para estimar la probabilidad de que alguien regrese a una habitación. */
@Component
public class GeminiClient {

    private final HomeIaProperties.Gemini properties;
    private final RestClient restClient;

    public GeminiClient(HomeIaProperties properties) {
        this.properties = properties.gemini();
        // Gemini puede tardar varios segundos en generar el plan; más de 20 s se trata como "no respondió"
        this.restClient = HttpClients.withTimeouts(this.properties.baseUrl(), Duration.ofSeconds(5), Duration.ofSeconds(20));
    }

    public boolean configured() {
        return properties.configured();
    }

    /** Devuelve un número entre 0 y 1, o vacío si la respuesta no se pudo interpretar. */
    public Optional<Double> estimateReturnProbability(String context) {
        String prompt = "Eres el motor de automatización de energía de un hogar. "
                + "Con el historial de presencia siguiente, responde SOLO con un número entre 0 y 1 "
                + "que indique la probabilidad de que alguien regrese a la habitación en los próximos 15 minutos.\n\n"
                + context;

        String payload = Json.write(Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0, "maxOutputTokens", 16)));

        String response = restClient.post()
                .uri("/models/{model}:generateContent", properties.model())
                .header("x-goog-api-key", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        String text = Json.read(response).path("candidates").path(0).path("content").path("parts")
                .path(0).path("text").asText("").trim();
        return parseProbability(text);
    }

    /** Pide a Gemini una respuesta en JSON. Vacío si el modelo no devolvió texto. Lanza excepción si el HTTP falla. */
    public Optional<JsonNode> generateJson(String prompt) {
        String payload = Json.write(Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0, "responseMimeType", "application/json")));

        String response = restClient.post()
                .uri("/models/{model}:generateContent", properties.model())
                .header("x-goog-api-key", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        String text = Json.read(response).path("candidates").path(0).path("content").path("parts")
                .path(0).path("text").asText("").trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(Json.read(text));
    }

    static Optional<Double> parseProbability(String text) {
        try {
            double value = Double.parseDouble(text.replaceAll("[^0-9.]", ""));
            if (value > 1.0) {
                value = value / 100.0;
            }
            return value >= 0 && value <= 1 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
