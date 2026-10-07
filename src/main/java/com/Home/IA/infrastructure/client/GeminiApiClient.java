package com.home.ia.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class GeminiApiClient {

    private final String apiKey;
    private final String modelId;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    public String generateContent(String prompt) {
        try {
            String url = GEMINI_API_URL.replace("{model}", modelId) + "?key=" + apiKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "parts", List.of(
                                            Map.of("text", prompt)
                                    )
                            )
                    )
            );

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody),
                    headers
            );

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return parseResponse(response.getBody());
            } else {
                log.error("Gemini API error: HTTP {}", response.getStatusCode());
                throw new RuntimeException("Gemini API returned status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }

    private String parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode candidates = root.get("candidates");

            if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).get("content");
                if (content != null) {
                    JsonNode parts = content.get("parts");
                    if (parts != null && parts.isArray() && parts.size() > 0) {
                        String text = parts.get(0).get("text").asText();
                        log.debug("Gemini response text: {}", text);
                        return text;
                    }
                }
            }

            log.warn("Unexpected Gemini response format: {}", response);
            return "{}";
        } catch (Exception e) {
            log.error("Error parsing Gemini response: {}", e.getMessage(), e);
            return "{}";
        }
    }
}
