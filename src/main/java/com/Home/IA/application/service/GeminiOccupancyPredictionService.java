package com.home.ia.application.service;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeAIException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.home.ia.domain.model.home.RoomId;
import com.home.ia.infrastructure.persistence.entity.EnergyConsumptionHistoryEntity;
import com.home.ia.infrastructure.persistence.repository.EnergyConsumptionHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiOccupancyPredictionService {

    private final GenerativeModel geminiModel;
    private final EnergyConsumptionHistoryJpaRepository energyRepository;
    private final ConcurrentHashMap<String, CachedPrediction> predictionCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MINUTES = 5;

    public double predictOccupancyProbability(RoomId roomId, Instant now) {
        String roomIdStr = roomId.value().toString();

        // Verificar cache con expiración
        CachedPrediction cached = predictionCache.get(roomIdStr);
        if (cached != null && !cached.isExpired()) {
            log.debug("Cache hit for room {} - probability: {}", roomIdStr, cached.probability);
            return cached.probability;
        }

        try {
            double probability = analyzePredictWithGemini(roomId, now);
            predictionCache.put(roomIdStr, new CachedPrediction(probability, now));
            return probability;
        } catch (Exception e) {
            log.error("Error predicting occupancy for room {}: {}", roomIdStr, e.getMessage());
            // Fallback: usar cache antiguo si disponible
            if (cached != null) {
                log.warn("Usando predicción en cache expirada para room {}", roomIdStr);
                return cached.probability;
            }
            return 0.5;
        }
    }

    private double analyzePredictWithGemini(RoomId roomId, Instant now) {
        String roomIdStr = roomId.value().toString();
        List<EnergyConsumptionHistoryEntity> recentConsumption = getEnergyConsumptionForRoom(
                roomIdStr,
                now.minus(24, ChronoUnit.HOURS)
        );

        if (recentConsumption.isEmpty()) {
            log.debug("No recent consumption data for room {}", roomIdStr);
            return 0.5;
        }

        String prompt = buildAnalysisPrompt(recentConsumption, roomIdStr);
        String response = queryGemini(prompt);
        return parseGeminiResponse(response);
    }

    private String buildAnalysisPrompt(List<EnergyConsumptionHistoryEntity> consumptionData, String roomId) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analiza los siguientes datos de consumo de energía de una habitación y predice ")
              .append("la probabilidad de que esté ocupada en este momento (0.0 a 1.0).\n\n");

        prompt.append("ID de Habitación: ").append(roomId).append("\n");
        prompt.append("Datos de consumo (últimas 24 horas):\n");

        double avgConsumption = 0;
        for (EnergyConsumptionHistoryEntity data : consumptionData) {
            prompt.append(String.format("- Hora: %s, Consumo: %.2f W\n",
                    data.getMeasuredAt(),
                    data.getConsumptionWatts().doubleValue()));
            avgConsumption += data.getConsumptionWatts().doubleValue();
        }

        avgConsumption /= consumptionData.size();
        prompt.append(String.format("\nConsumo Promedio: %.2f W\n", avgConsumption));

        prompt.append("\nResponde SOLO con un JSON válido en este formato exacto:\n");
        prompt.append("{\"probability\": 0.75, \"reasoning\": \"breve explicación\"}\n");
        prompt.append("La probabilidad debe ser un número entre 0.0 y 1.0");

        return prompt.toString();
    }

    private String queryGemini(String prompt) {
        try {
            log.debug("Querying Gemini for occupancy prediction");
            var response = geminiModel.generateContent(prompt);
            String content = response.getContent().getParts().stream()
                    .map(part -> part.asText())
                    .findFirst()
                    .orElse("{}");
            log.debug("Gemini response: {}", content);
            return content;
        } catch (GenerativeAIException e) {
            log.error("Gemini API error: {}", e.getMessage());
            throw new RuntimeException("Error calling Gemini API", e);
        }
    }

    private double parseGeminiResponse(String response) {
        try {
            // Intentar parsear como JSON
            String cleanedResponse = response.replaceAll("```json|```", "").trim();
            JsonObject json = JsonParser.parseString(cleanedResponse).getAsJsonObject();

            if (json.has("probability")) {
                double probability = json.get("probability").getAsDouble();
                if (probability < 0.0 || probability > 1.0) {
                    log.warn("Invalid probability from Gemini: {}, using default", probability);
                    return 0.5;
                }
                return probability;
            }
        } catch (Exception e) {
            log.warn("Error parsing Gemini response as JSON: {}", e.getMessage());
        }

        // Fallback: intentar extraer número del texto
        try {
            String[] parts = response.split("\\D+");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    double value = Double.parseDouble(part) / 100.0;
                    if (value >= 0.0 && value <= 1.0) {
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract probability from response");
        }

        return 0.5;
    }

    private List<EnergyConsumptionHistoryEntity> getEnergyConsumptionForRoom(String roomId, Instant afterTime) {
        try {
            return energyRepository.findByDeviceIdAndMeasuredAtAfter(roomId, afterTime);
        } catch (Exception e) {
            log.warn("Failed to retrieve energy consumption data for room {}: {}", roomId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static class CachedPrediction {
        final double probability;
        final long timestamp;

        CachedPrediction(double probability, Instant timestamp) {
            this.probability = probability;
            this.timestamp = timestamp.toEpochMilli();
        }

        boolean isExpired() {
            long ageMinutes = (System.currentTimeMillis() - timestamp) / 60000;
            return ageMinutes > CACHE_DURATION_MINUTES;
        }
    }
}
