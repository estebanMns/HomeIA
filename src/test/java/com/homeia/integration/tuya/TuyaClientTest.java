package com.homeia.integration.tuya;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.integration.Json;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueba el cliente contra una nube de Tuya simulada que verifica la firma de cada petición. */
class TuyaClientTest {

    private static final String ID = "test-client-id";
    private static final String SECRET = "test-secret-0123456789abcdef0123";

    /** Nube simulada: valida la firma como lo haría Tuya y responde según la ruta. */
    static final class FakeTuya implements TuyaTransport {
        final List<String> calls = new ArrayList<>();
        String lastCommandBody;
        int tokensIssued;
        boolean rejectNextBusinessCall;

        @Override
        public String exchange(String method, String url, Map<String, String> h, String body) {
            calls.add(method + " " + url);
            String token = h.get("access_token");
            String expected = TuyaSigner.sign(ID, SECRET, token, h.get("t"), h.get("nonce"),
                    TuyaSigner.stringToSign(method, body, Map.of(), url));
            if (!expected.equals(h.get("sign")) || !ID.equals(h.get("client_id"))
                    || !"HMAC-SHA256".equals(h.get("sign_method"))) {
                return "{\"success\":false,\"code\":1004,\"msg\":\"sign invalid\"}";
            }
            if (url.startsWith("/v1.0/token")) {
                tokensIssued++;
                return "{\"success\":true,\"result\":{\"access_token\":\"tok" + tokensIssued
                        + "\",\"refresh_token\":\"ref" + tokensIssued + "\",\"expire_time\":7200,\"uid\":\"u1\"}}";
            }
            if (rejectNextBusinessCall) {
                rejectNextBusinessCall = false;
                return "{\"success\":false,\"code\":1010,\"msg\":\"token invalid\"}";
            }
            if (url.startsWith("/v2.0/cloud/thing/device")) {
                if (!url.contains("last_id")) {
                    StringBuilder page = new StringBuilder("{\"success\":true,\"result\":[");
                    for (int i = 0; i < 20; i++) {
                        if (i > 0) page.append(',');
                        page.append("{\"id\":\"dev").append(i).append("\",\"name\":\"Enchufe ").append(i)
                                .append("\",\"custom_name\":\"\",\"category\":\"cz\",\"product_name\":\"Smart Plug\",\"is_online\":true}");
                    }
                    return page.append("]}").toString();
                }
                return "{\"success\":true,\"result\":[{\"id\":\"dev20\",\"name\":\"Bombillo\",\"custom_name\":\"Lámpara sala\","
                        + "\"category\":\"dj\",\"product_name\":\"Smart Bulb\",\"is_online\":false}]}";
            }
            if (url.endsWith("/status")) {
                return "{\"success\":true,\"result\":[{\"code\":\"switch_1\",\"value\":true},{\"code\":\"cur_power\",\"value\":1503}]}";
            }
            if (url.endsWith("/specification")) {
                return "{\"success\":true,\"result\":{\"category\":\"cz\",\"functions\":[{\"code\":\"switch_1\",\"type\":\"Boolean\",\"values\":\"{}\"}],"
                        + "\"status\":[{\"code\":\"cur_power\",\"type\":\"Integer\",\"values\":\"{\\\"unit\\\":\\\"W\\\",\\\"min\\\":0,\\\"max\\\":99999,\\\"scale\\\":1,\\\"step\\\":1}\"}]}}";
            }
            if (url.endsWith("/commands")) {
                lastCommandBody = body;
                return "{\"success\":true,\"result\":true}";
            }
            return "{\"success\":false,\"code\":404,\"msg\":\"no encontrado\"}";
        }
    }

    private static TuyaClient client(FakeTuya fake) {
        return new TuyaClient(ID, SECRET, fake, Clock.fixed(Instant.parse("2026-10-20T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void listaTodosLosDispositivosPaginandoDe20En20() {
        FakeTuya fake = new FakeTuya();
        List<TuyaCloudDevice> devices = client(fake).listDevices();
        assertEquals(21, devices.size());
        assertEquals("Lámpara sala", devices.get(20).name(), "usa el nombre personalizado si existe");
        assertFalse(devices.get(20).online());
        assertTrue(fake.calls.contains("GET /v2.0/cloud/thing/device?last_id=dev19&page_size=20"));
    }

    @Test
    void pideElTokenUnaSolaVezYLoReutiliza() {
        FakeTuya fake = new FakeTuya();
        TuyaClient client = client(fake);
        client.status("dev1");
        client.status("dev2");
        assertEquals(1, fake.tokensIssued);
    }

    @Test
    void siTuyaRechazaElTokenPideUnoNuevoYReintenta() {
        FakeTuya fake = new FakeTuya();
        TuyaClient client = client(fake);
        client.status("dev1");
        fake.rejectNextBusinessCall = true;
        List<TuyaDataPoint> status = client.status("dev1");
        assertEquals(2, fake.tokensIssued);
        assertEquals(2, status.size());
    }

    @Test
    void leeElEstadoYLaEscalaDelDispositivo() {
        FakeTuya fake = new FakeTuya();
        TuyaClient client = client(fake);
        List<TuyaDataPoint> status = client.status("dev1");
        assertEquals(new TuyaDataPoint("switch_1", true), status.get(0));
        assertEquals(new TuyaDataPoint("cur_power", 1503L), status.get(1));
        Map<String, TuyaScale> spec = client.specification("dev1");
        assertEquals(1, spec.get("cur_power").scale());
        assertEquals(150.3, TuyaStateMapper.toReport(status, spec).get("consumptionWatts"));
    }

    @Test
    void enviaElComandoConElFormatoDeTuya() {
        FakeTuya fake = new FakeTuya();
        client(fake).sendCommands("dev1", List.of(new TuyaDataPoint("switch_1", false)));
        JsonNode body = Json.read(fake.lastCommandBody);
        assertEquals("switch_1", body.path("commands").path(0).path("code").asText());
        assertFalse(body.path("commands").path(0).path("value").asBoolean(true));
        assertTrue(fake.calls.contains("POST /v1.0/iot-03/devices/dev1/commands"));
    }

    @Test
    void unaFirmaIncorrectaDaUnMensajeClaro() {
        TuyaClient malo = new TuyaClient(ID, "otra-clave", new FakeTuya(),
                Clock.fixed(Instant.parse("2026-10-20T04:00:00Z"), ZoneOffset.UTC));
        try {
            malo.listDevices();
        } catch (TuyaApiException e) {
            assertEquals(1004, e.code());
            assertTrue(e.getMessage().contains("TUYA_ACCESS_SECRET"));
            return;
        }
        throw new AssertionError("debió fallar");
    }
}
