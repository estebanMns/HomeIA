package com.homeia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración de HomeIA. Todos los valores vienen de variables de entorno (.env), nada va fijo en código. */
@ConfigurationProperties(prefix = "homeia")
public record HomeIaProperties(
        Jwt jwt,
        Cors cors,
        Mqtt mqtt,
        WhatsApp whatsapp,
        Gemini gemini,
        Automation automation,
        Defaults defaults
) {

    /**
     * Valores iniciales de la casa. El registro solo pide la cuenta; lo demás se toma de aquí
     * y se puede cambiar después en "Usuarios y casa" y "Automatización".
     */
    public record Defaults(String homeName, String currency, String timezone,
                           java.math.BigDecimal electricityPricePerKwh, int vacancyMinutes,
                           int returnSensitivityPercent, double consumptionAlertWatts, int leakMinutes) {
    }

    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenMinutes) {
    }

    public record Cors(String allowedOrigins) {
    }

    public record Mqtt(boolean enabled, String brokerUrl, String username, String password,
                       String clientId, String topicPrefix) {
    }

    /**
     * @param alertTemplate    nombre de la plantilla aprobada en Meta para las alertas. Meta solo permite texto
     *                         libre dentro de las 24 h siguientes a que el usuario escriba al número; los avisos que
     *                         inicia HomeIA deben ir como plantilla. Vacío = texto libre (solo para pruebas).
     * @param templateLanguage código de idioma de la plantilla, p. ej. "es" o "es_CO".
     */
    public record WhatsApp(String accessToken, String phoneNumberId, String apiBaseUrl,
                           String alertTemplate, String templateLanguage) {

        public boolean configured() {
            return accessToken != null && !accessToken.isBlank()
                    && phoneNumberId != null && !phoneNumberId.isBlank();
        }

        public boolean usesTemplate() {
            return alertTemplate != null && !alertTemplate.isBlank();
        }

        public String languageOrDefault() {
            return templateLanguage == null || templateLanguage.isBlank() ? "es" : templateLanguage;
        }
    }

    public record Gemini(String apiKey, String model, String baseUrl, long decisionIntervalSeconds) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Automation(long cycleSeconds) {
    }
}
