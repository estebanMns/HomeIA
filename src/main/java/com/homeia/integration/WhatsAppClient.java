package com.homeia.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.config.HomeIaProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Cliente de la WhatsApp Cloud API (Meta). Las credenciales vienen de variables de entorno. */
@Component
public class WhatsAppClient {

    private final HomeIaProperties.WhatsApp properties;
    private final RestClient restClient;

    public WhatsAppClient(HomeIaProperties properties) {
        this.properties = properties.whatsapp();
        this.restClient = RestClient.builder()
                .baseUrl(this.properties.apiBaseUrl())
                .build();
    }

    public boolean configured() {
        return properties.configured();
    }

    /** Envía un mensaje de texto y devuelve el id asignado por Meta. Lanza excepción si falla. */
    public String sendText(String toE164, String body) {
        if (!configured()) {
            throw new IllegalStateException("WhatsApp no está configurado (WHATSAPP_ACCESS_TOKEN / WHATSAPP_PHONE_NUMBER_ID)");
        }
        String payload = Json.write(Map.of(
                "messaging_product", "whatsapp",
                "to", toE164.replace("+", ""),
                "type", "text",
                "text", Map.of("body", body)));

        String response = restClient.post()
                .uri("/{phoneNumberId}/messages", properties.phoneNumberId())
                .header("Authorization", "Bearer " + properties.accessToken())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        JsonNode id = Json.read(response).path("messages").path(0).path("id");
        if (id.isMissingNode() || id.asText().isBlank()) {
            throw new IllegalStateException("Meta no devolvió id de mensaje: " + response);
        }
        return id.asText();
    }
}
