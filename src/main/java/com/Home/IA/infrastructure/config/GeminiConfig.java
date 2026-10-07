package com.home.ia.infrastructure.config;

import com.home.ia.infrastructure.client.GeminiApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class GeminiConfig {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model-id:gemini-2.0-flash}")
    private String modelId;

    @Bean
    public GeminiApiClient geminiApiClient(RestTemplate restTemplate) {
        log.info("Inicializando cliente Gemini con modelo: {}", modelId);
        return new GeminiApiClient(apiKey, modelId, restTemplate);
    }
}
