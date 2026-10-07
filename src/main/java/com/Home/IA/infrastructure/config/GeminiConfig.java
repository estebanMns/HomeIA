package com.home.ia.infrastructure.config;

import com.google.ai.client.generativeai.GenerativeModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class GeminiConfig {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model-id:gemini-2.0-flash}")
    private String modelId;

    @Bean
    public GenerativeModel geminiModel() {
        log.info("Inicializando modelo Gemini: {}", modelId);
        return new GenerativeModel(modelId, apiKey);
    }
}
