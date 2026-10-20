package com.homeia.integration.tuya;

import com.homeia.config.HomeIaProperties;
import com.homeia.integration.HttpClients;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Punto de entrada a Tuya Cloud para el resto del backend. Si faltan TUYA_ACCESS_ID / TUYA_ACCESS_SECRET,
 * {@link #configured()} es false y cualquier operación responde con un mensaje que explica qué configurar.
 */
@Component
public class TuyaGateway {

    private final HomeIaProperties.Tuya properties;
    private final TuyaClient client;
    /** La especificación de un dispositivo no cambia: se pide una vez y se guarda. */
    private final Map<String, Map<String, TuyaScale>> specCache = new ConcurrentHashMap<>();
    /** Última lectura de cada dispositivo: sirve para saber sus códigos (switch_1, switch_led…) sin volver a pedirla. */
    private final Map<String, List<TuyaDataPoint>> lastStatus = new ConcurrentHashMap<>();

    public TuyaGateway(HomeIaProperties properties) {
        this.properties = properties.tuya();
        if (this.properties != null && this.properties.configured()) {
            String baseUrl = this.properties.resolvedBaseUrl();
            RestClient http = HttpClients.withTimeouts(baseUrl, Duration.ofSeconds(5), Duration.ofSeconds(10));
            TuyaTransport transport = (method, url, headers, body) -> {
                var request = http.method(HttpMethod.valueOf(method)).uri(URI.create(baseUrl + url));
                headers.forEach(request::header);
                if (body != null) {
                    request.contentType(MediaType.APPLICATION_JSON).body(body);
                }
                // Tuya responde 200 incluso con errores (success=false); el cliente revisa el cuerpo.
                return request.retrieve().body(String.class);
            };
            this.client = new TuyaClient(TuyaCredentials.clean(this.properties.accessId()),
                    TuyaCredentials.clean(this.properties.accessSecret()), transport, Clock.systemUTC());
        } else {
            this.client = null;
        }
    }

    public boolean configured() {
        return client != null;
    }

    public String region() {
        return properties == null ? "us" : properties.region();
    }

    public String baseUrl() {
        return properties == null ? "" : properties.resolvedBaseUrl();
    }

    public List<TuyaCloudDevice> listDevices() {
        return client().listDevices();
    }

    public List<TuyaDataPoint> status(String deviceId) {
        List<TuyaDataPoint> status = client().status(deviceId);
        lastStatus.put(deviceId, status);
        return status;
    }

    /** Última lectura conocida del dispositivo, o una nueva si nunca se ha leído. */
    public List<TuyaDataPoint> knownStatus(String deviceId) {
        List<TuyaDataPoint> cached = lastStatus.get(deviceId);
        return cached != null ? cached : status(deviceId);
    }

    public Map<String, TuyaScale> specification(String deviceId) {
        return specCache.computeIfAbsent(deviceId, id -> {
            try {
                return client().specification(id);
            } catch (TuyaApiException e) {
                return Map.of(); // sin especificación se usan las escalas por defecto
            }
        });
    }

    public void sendCommands(String deviceId, List<TuyaDataPoint> commands) {
        client().sendCommands(deviceId, commands);
    }

    private TuyaClient client() {
        if (client == null) {
            throw new TuyaApiException(0,
                    "Tuya no está configurado: agrega TUYA_ACCESS_ID y TUYA_ACCESS_SECRET al .env y reinicia el backend");
        }
        return client;
    }
}
