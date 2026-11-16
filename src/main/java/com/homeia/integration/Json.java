package com.homeia.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Utilidad JSON para los clientes externos (MQTT, WhatsApp, Gemini). */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar a JSON", e);
        }
    }

    public static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON inválido: " + e.getMessage(), e);
        }
    }

    /** Objeto JSON como Map de tipos simples (para respuestas de la API, que usan otro serializador). */
    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> toMap(String json) {
        try {
            Object value = MAPPER.readValue(json == null || json.isBlank() ? "{}" : json, Object.class);
            return value instanceof java.util.Map<?, ?> map ? (java.util.Map<String, Object>) map : java.util.Map.of();
        } catch (Exception e) {
            return java.util.Map.of();
        }
    }
}
