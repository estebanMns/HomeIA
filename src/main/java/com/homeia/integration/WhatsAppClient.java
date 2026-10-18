package com.homeia.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.config.HomeIaProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Cliente de la WhatsApp Cloud API (Meta). Las credenciales vienen de variables de entorno. */
@Component
public class WhatsAppClient {

    private final HomeIaProperties.WhatsApp properties;
    private final RestClient restClient;

    public WhatsAppClient(HomeIaProperties properties) {
        this.properties = properties.whatsapp();
        this.restClient = HttpClients.withTimeouts(this.properties.apiBaseUrl(), Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    public boolean configured() {
        return properties.configured();
    }

    /** True si las alertas deben ir como plantilla aprobada (necesario fuera de la ventana de 24 h de Meta). */
    public boolean usesTemplate() {
        return properties.usesTemplate();
    }

    /** Envía un mensaje de texto libre y devuelve el id asignado por Meta. Lanza excepción si falla. */
    public String sendText(String toE164, String body) {
        return send(Map.of(
                "messaging_product", "whatsapp",
                "to", toE164.replace("+", ""),
                "type", "text",
                "text", Map.of("body", body)));
    }

    /**
     * Envía la plantilla de alertas configurada con sus variables de cuerpo, en orden ({{1}}, {{2}}, ...).
     * Las variables ya deben venir limpias: sin saltos de línea, tabulaciones ni más de 4 espacios seguidos.
     */
    public String sendAlertTemplate(String toE164, List<String> bodyParameters) {
        if (!usesTemplate()) {
            throw new IllegalStateException("No hay plantilla de alertas configurada (WHATSAPP_ALERT_TEMPLATE)");
        }
        List<Map<String, String>> parameters = bodyParameters.stream()
                .map(text -> Map.of("type", "text", "text", text))
                .toList();
        return send(Map.of(
                "messaging_product", "whatsapp",
                "to", toE164.replace("+", ""),
                "type", "template",
                "template", Map.of(
                        "name", properties.alertTemplate(),
                        "language", Map.of("code", properties.languageOrDefault()),
                        "components", List.of(Map.of("type", "body", "parameters", parameters)))));
    }

    private String send(Map<String, Object> payload) {
        if (!configured()) {
            throw new IllegalStateException("WhatsApp no está configurado (WHATSAPP_ACCESS_TOKEN / WHATSAPP_PHONE_NUMBER_ID)");
        }
        String response = restClient.post()
                .uri("/{phoneNumberId}/messages", properties.phoneNumberId())
                .header("Authorization", "Bearer " + properties.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Json.write(payload))
                .retrieve()
                .body(String.class);

        JsonNode id = Json.read(response).path("messages").path(0).path("id");
        if (id.isMissingNode() || id.asText().isBlank()) {
            throw new IllegalStateException("Meta no devolvió id de mensaje: " + response);
        }
        return id.asText();
    }
}
