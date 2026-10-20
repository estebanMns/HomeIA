package com.homeia.integration.tuya;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Firma de peticiones de la API de Tuya Cloud (esquema actual con stringToSign).
 *
 * <pre>
 * stringToSign = HTTPMethod + "\n" + SHA256(body) + "\n" + cabecerasFirmadas + "\n" + URL
 * str (token)    = client_id + t + nonce + stringToSign
 * str (negocio)  = client_id + access_token + t + nonce + stringToSign
 * sign           = HMAC-SHA256(str, secret) en hexadecimal mayúsculas
 * </pre>
 * La URL lleva la ruta y, si hay parámetros, "?" con los parámetros ordenados por nombre.
 * Referencia: https://developer.tuya.com/en/docs/iot/new-singnature?id=Kbw0q34cs2e5g
 */
public final class TuyaSigner {

    static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private TuyaSigner() {
    }

    /** Ruta + parámetros ordenados por nombre, tal como se firman y se envían. */
    public static String url(String path, Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return path;
        }
        StringBuilder url = new StringBuilder(path).append('?');
        boolean first = true;
        for (Map.Entry<String, String> e : new TreeMap<>(query).entrySet()) {
            if (!first) url.append('&');
            url.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return url.toString();
    }

    /**
     * @param signedHeaders cabeceras incluidas en la firma (las de Signature-Headers), en orden; vacío si no hay
     */
    public static String stringToSign(String method, String body, Map<String, String> signedHeaders, String url) {
        StringBuilder headers = new StringBuilder();
        if (signedHeaders != null) {
            signedHeaders.forEach((k, v) -> headers.append(k).append(':').append(v).append('\n'));
        }
        return method.toUpperCase(Locale.ROOT) + "\n" + sha256(body) + "\n" + headers + "\n" + url;
    }

    /** @param accessToken null para las llamadas de obtención y renovación del token */
    public static String sign(String clientId, String secret, String accessToken, String t, String nonce,
                              String stringToSign) {
        String str = clientId + (accessToken == null ? "" : accessToken) + t + (nonce == null ? "" : nonce)
                + stringToSign;
        return hmacSha256(secret, str);
    }

    static String sha256(String body) {
        if (body == null || body.isEmpty()) {
            return EMPTY_BODY_SHA256;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hmacSha256(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar la petición a Tuya", e);
        }
    }
}
