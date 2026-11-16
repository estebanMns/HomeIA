package com.homeia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** Configuración de HomeIA. Todos los valores vienen de variables de entorno (.env), nada va fijo en código. */
@ConfigurationProperties(prefix = "homeia")
public record HomeIaProperties(
        Jwt jwt,
        Cors cors,
        Mqtt mqtt,
        WhatsApp whatsapp,
        Gemini gemini,
        Tuya tuya,
        Automation automation,
        Defaults defaults,
        Security security,
        Edge edge,
        Retention retention
) {

    /**
     * @param secretsKey clave AES-256 (32 bytes en base64) para cifrar secretos de Edge y credenciales de
     *                   dispositivos. Generar con: openssl rand -base64 32
     */
    public record Security(String secretsKey) {
    }

    /**
     * HomeIA Edge.
     * @param topicRoot               raíz de los tópicos: {topicRoot}/{edgeId}/{tipo}
     * @param heartbeatTimeoutSeconds sin heartbeat durante este tiempo el Edge se marca OFFLINE
     * @param replayWindowSeconds     tolerancia de reloj y ventana anti-repetición de los mensajes firmados
     */
    public record Edge(String topicRoot, long heartbeatTimeoutSeconds, long replayWindowSeconds) {
        public String root() {
            return topicRoot == null || topicRoot.isBlank() ? "homeia/edge" : topicRoot.trim().replaceAll("/+$", "");
        }

        public long heartbeatTimeout() {
            return heartbeatTimeoutSeconds > 0 ? heartbeatTimeoutSeconds : 90;
        }

        public long replayWindow() {
            return replayWindowSeconds > 0 ? replayWindowSeconds : 120;
        }
    }

    /** Días que se conservan los datos de alto volumen. 0 o negativo = no se borran. */
    public record Retention(int energyReadingDays, int deviceEventDays, int deviceCommandDays) {
    }

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

    /**
     * @param caCertPath     PEM de la CA del broker (ssl://). Vacío = almacén de confianza de la JVM.
     * @param clientCertPath PEM del certificado de cliente para mTLS (opcional)
     * @param clientKeyPath  PEM PKCS#8 de la clave del certificado de cliente (opcional)
     */
    public record Mqtt(boolean enabled, String brokerUrl, String username, String password,
                       String clientId, String topicPrefix, String caCertPath, String clientCertPath,
                       String clientKeyPath) {

        public Mqtt(boolean enabled, String brokerUrl, String username, String password, String clientId,
                    String topicPrefix) {
            this(enabled, brokerUrl, username, password, clientId, topicPrefix, null, null, null);
        }

        /** Quita espacios y comillas que se cuelan al copiar valores en el .env. */
        @ConstructorBinding
        public Mqtt {
            brokerUrl = clean(brokerUrl);
            username = clean(username);
            clientId = clean(clientId);
            topicPrefix = clean(topicPrefix);
            caCertPath = clean(caCertPath);
            clientCertPath = clean(clientCertPath);
            clientKeyPath = clean(clientKeyPath);
        }

        public boolean tls() {
            return brokerUrl != null && (brokerUrl.startsWith("ssl://") || brokerUrl.startsWith("wss://"));
        }

        static String clean(String value) {
            if (value == null) return null;
            String v = value.trim();
            while (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
                v = v.substring(1, v.length() - 1).trim();
            }
            return v;
        }
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

    /** @param timeoutSeconds espera máxima por respuesta: los modelos Gemini 3 razonan y tardan decenas de segundos */
    public record Gemini(String apiKey, String model, String baseUrl, long decisionIntervalSeconds, long timeoutSeconds) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Automation(long cycleSeconds) {
    }

    /**
     * Tuya Cloud (dispositivos Tuya / Smart Life por WiFi).
     * @param region      centro de datos del proyecto: us (Western America), us-e, eu, eu-w, cn, in
     * @param baseUrl     opcional: reemplaza la URL de la región
     * @param syncSeconds cada cuánto se lee el estado de los dispositivos (cada lectura cuenta en la cuota de Tuya)
     */
    public record Tuya(String accessId, String accessSecret, String region, String baseUrl, long syncSeconds) {

        public boolean configured() {
            return accessId != null && !accessId.isBlank() && accessSecret != null && !accessSecret.isBlank();
        }

        public String resolvedBaseUrl() {
            if (baseUrl != null && !baseUrl.isBlank()) {
                return baseUrl.trim().replaceAll("/+$", "");
            }
            return switch (region == null ? "us" : region.trim().toLowerCase()) {
                case "us-e" -> "https://openapi-ueaz.tuyaus.com";
                case "eu" -> "https://openapi.tuyaeu.com";
                case "eu-w" -> "https://openapi-weaz.tuyaeu.com";
                case "cn" -> "https://openapi.tuyacn.com";
                case "in" -> "https://openapi.tuyain.com";
                default -> "https://openapi.tuyaus.com";
            };
        }
    }
}
