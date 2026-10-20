package com.homeia.integration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient con tiempos límite. Sin ellos, una API externa lenta (Gemini, WhatsApp) dejaba bloqueado
 * el ciclo de automatización indefinidamente.
 */
public final class HttpClients {

    private HttpClients() {
    }

    public static RestClient withTimeouts(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
