package com.homeia.integration.tuya;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.integration.Json;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cliente de la API de Tuya Cloud (Tuya IoT Platform). Controla dispositivos Tuya / Smart Life
 * por la nube, igual que la app Smart Life: los dispositivos siguen conectados a su WiFi de siempre.
 *
 * Maneja el token de acceso (lo obtiene, lo renueva un minuto antes de que venza y, si Tuya lo
 * rechaza, lo pide de nuevo una vez) y firma cada petición con {@link TuyaSigner}.
 */
public class TuyaClient {

    /** Errores de Tuya que indican token inválido o vencido. */
    private static final List<Integer> TOKEN_ERRORS = List.of(1010, 1011, 1012);
    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGES = 25;

    private final String clientId;
    private final String secret;
    private final TuyaTransport transport;
    private final Clock clock;

    private String accessToken;
    private String refreshToken;
    private long tokenExpiresAtMillis;

    public TuyaClient(String clientId, String secret, TuyaTransport transport, Clock clock) {
        this.clientId = clientId;
        this.secret = secret;
        this.transport = transport;
        this.clock = clock;
    }

    // ---------- Operaciones ----------

    /** Dispositivos de la cuenta Smart Life vinculada al proyecto de Tuya. */
    public List<TuyaCloudDevice> listDevices() {
        List<TuyaCloudDevice> devices = new ArrayList<>();
        String lastId = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("page_size", String.valueOf(PAGE_SIZE));
            if (lastId != null) query.put("last_id", lastId);
            JsonNode result = call("GET", "/v2.0/cloud/thing/device", query, null);
            int count = 0;
            for (JsonNode d : result) {
                String name = d.path("custom_name").asText("");
                if (name.isBlank()) name = d.path("name").asText("Dispositivo Tuya");
                devices.add(new TuyaCloudDevice(d.path("id").asText(), name, d.path("category").asText(""),
                        d.path("product_name").asText(""), d.path("is_online").asBoolean(false)));
                lastId = d.path("id").asText();
                count++;
            }
            if (count < PAGE_SIZE) break;
        }
        return devices;
    }

    /** Valores actuales del dispositivo (switch_1, cur_power, bright_value_v2…). */
    public List<TuyaDataPoint> status(String deviceId) {
        JsonNode result = call("GET", "/v1.0/iot-03/devices/" + deviceId + "/status", Map.of(), null);
        List<TuyaDataPoint> points = new ArrayList<>();
        for (JsonNode item : result) {
            points.add(new TuyaDataPoint(item.path("code").asText(), plain(item.path("value"))));
        }
        return points;
    }

    /**
     * Escala y máximo de cada valor numérico según la especificación del dispositivo. Por ejemplo,
     * cur_power con scale 1 significa que 1234 equivale a 123,4 W.
     */
    public Map<String, TuyaScale> specification(String deviceId) {
        JsonNode result = call("GET", "/v1.0/iot-03/devices/" + deviceId + "/specification", Map.of(), null);
        Map<String, TuyaScale> scales = new HashMap<>();
        for (String section : List.of("functions", "status")) {
            for (JsonNode item : result.path(section)) {
                String rawValues = item.path("values").asText("");
                if (rawValues.isBlank() || !rawValues.trim().startsWith("{")) continue;
                JsonNode values = Json.read(rawValues);
                if (values.has("scale") || values.has("max")) {
                    scales.putIfAbsent(item.path("code").asText(),
                            new TuyaScale(values.path("scale").asInt(0), values.path("min").asDouble(0),
                                    values.path("max").asDouble(0)));
                }
            }
        }
        return scales;
    }

    /** Envía comandos, p. ej. [{"code":"switch_1","value":false}]. */
    public void sendCommands(String deviceId, List<TuyaDataPoint> commands) {
        List<Map<String, Object>> body = commands.stream()
                .map(c -> Map.<String, Object>of("code", c.code(), "value", c.value()))
                .toList();
        JsonNode result = call("POST", "/v1.0/iot-03/devices/" + deviceId + "/commands", Map.of(),
                Json.write(Map.of("commands", body)));
        if (result.isBoolean() && !result.asBoolean()) {
            throw new TuyaApiException(0, "Tuya no aceptó el comando para el dispositivo " + deviceId);
        }
    }

    // ---------- Peticiones firmadas ----------

    private JsonNode call(String method, String path, Map<String, String> query, String body) {
        try {
            return callOnce(method, path, query, body);
        } catch (TuyaApiException e) {
            if (!TOKEN_ERRORS.contains(e.code())) throw e;
            synchronized (this) {
                accessToken = null; // Tuya rechazó el token: se pide uno nuevo y se reintenta una vez
            }
            return callOnce(method, path, query, body);
        }
    }

    private JsonNode callOnce(String method, String path, Map<String, String> query, String body) {
        String token = token();
        return send(method, TuyaSigner.url(path, query), body, token);
    }

    private synchronized String token() {
        long now = clock.millis();
        if (accessToken != null && now < tokenExpiresAtMillis - 60_000) {
            return accessToken;
        }
        JsonNode result = null;
        if (refreshToken != null && accessToken != null) {
            try {
                result = send("GET", "/v1.0/token/" + refreshToken, null, null);
            } catch (TuyaApiException e) {
                result = null; // si la renovación falla, se pide un token nuevo
            }
        }
        if (result == null) {
            result = send("GET", TuyaSigner.url("/v1.0/token", Map.of("grant_type", "1")), null, null);
        }
        accessToken = result.path("access_token").asText(null);
        refreshToken = result.path("refresh_token").asText(null);
        long expireSeconds = result.has("expire_time") ? result.path("expire_time").asLong() : result.path("expire").asLong(3600);
        tokenExpiresAtMillis = clock.millis() + expireSeconds * 1000;
        if (accessToken == null || accessToken.isBlank()) {
            throw new TuyaApiException(0, "Tuya no entregó un token de acceso");
        }
        return accessToken;
    }

    /** accessToken null = petición de token (se firma sin token). */
    private JsonNode send(String method, String url, String body, String accessToken) {
        String t = String.valueOf(clock.millis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String sign = TuyaSigner.sign(clientId, secret, accessToken, t, nonce,
                TuyaSigner.stringToSign(method, body, Map.of(), url));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("client_id", clientId);
        headers.put("sign", sign);
        headers.put("sign_method", "HMAC-SHA256");
        headers.put("t", t);
        headers.put("nonce", nonce);
        headers.put("lang", "es");
        if (accessToken != null) headers.put("access_token", accessToken);

        String response;
        try {
            response = transport.exchange(method, url, headers, body);
        } catch (RuntimeException e) {
            throw new TuyaApiException("No se pudo conectar con la nube de Tuya: " + e.getMessage(), e);
        }
        JsonNode root = Json.read(response == null || response.isBlank() ? "{}" : response);
        if (!root.path("success").asBoolean(false)) {
            throw new TuyaApiException(root.path("code").asInt(0), describe(root));
        }
        return root.path("result");
    }

    private static String describe(JsonNode root) {
        int code = root.path("code").asInt(0);
        String msg = root.path("msg").asText("sin detalle");
        return switch (code) {
            case 1004 -> "Tuya rechazó la firma (1004). Revisa que TUYA_ACCESS_SECRET sea el Access Secret del proyecto";
            case 2009 -> "Tuya no reconoce el Access ID (2009). Copia de nuevo el Access ID/Client ID de la pestaña "
                    + "Overview del proyecto y revisa que TUYA_REGION corresponda a su centro de datos "
                    + "(us = Western America)";
            case 1106 -> "Tuya negó el permiso (1106). Revisa que la cuenta Smart Life esté vinculada al proyecto, "
                    + "que el centro de datos sea el correcto y que el servicio IoT Core esté autorizado";
            default -> "Tuya respondió con error " + code + ": " + msg;
        };
    }

    /** Convierte un valor JSON en Boolean, Number o String. */
    static Object plain(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) return null;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isIntegralNumber()) return value.asLong();
        if (value.isNumber()) return value.asDouble();
        return value.isTextual() ? value.asText() : value.toString();
    }

    public Optional<String> clientId() {
        return Optional.ofNullable(clientId);
    }
}
