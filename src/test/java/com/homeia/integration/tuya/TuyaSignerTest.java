package com.homeia.integration.tuya;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Valores tomados de los ejemplos oficiales de Tuya para el esquema de firma actual. */
class TuyaSignerTest {

    private static final String CLIENT_ID = "1KAD46OrT9HafiKdsXeg";
    private static final String SECRET = "4OHBOnWOqaEC1mWXOpVL3yV50s0qGSRC";
    private static final String T = "1588925778000";
    private static final String NONCE = "5138cc3a9033d69856923fd07b491173";

    private static Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("area_id", "29a33e8796834b1efa6");
        h.put("call_id", "8afdb70ab2ed11eb85290242ac130003");
        return h;
    }

    @Test
    void firmaDeLaPeticionDeTokenComoElEjemploOficial() {
        String url = TuyaSigner.url("/v1.0/token", Map.of("grant_type", "1"));
        String sts = TuyaSigner.stringToSign("GET", "", headers(), url);
        assertEquals("9E48A3E93B302EEECC803C7241985D0A34EB944F40FB573C7B5C2A82158AF13E",
                TuyaSigner.sign(CLIENT_ID, SECRET, null, T, NONCE, sts));
    }

    @Test
    void firmaDeUnaPeticionDeNegocioComoElEjemploOficial() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page_size", "50");
        query.put("page_no", "1"); // en desorden a propósito: la URL firmada los ordena
        String url = TuyaSigner.url("/v2.0/apps/schema/users", query);
        assertEquals("/v2.0/apps/schema/users?page_no=1&page_size=50", url);
        String sts = TuyaSigner.stringToSign("GET", "", headers(), url);
        assertEquals("AE4481C692AA80B25F3A7E12C3A5FD9BBF6251539DD78E565A1A72A508A88784",
                TuyaSigner.sign(CLIENT_ID, SECRET, "3f4eda2bdec17232f67c0b188af3eec1", T, NONCE, sts));
    }

    @Test
    void cuerpoVacioUsaElHashOficial() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", TuyaSigner.sha256(""));
        assertEquals("GET\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n\n/v1.0/devices/abc",
                TuyaSigner.stringToSign("get", null, Map.of(), "/v1.0/devices/abc"));
    }
}
