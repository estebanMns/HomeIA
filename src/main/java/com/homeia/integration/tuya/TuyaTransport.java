package com.homeia.integration.tuya;

import java.util.Map;

/** Envía una petición HTTP ya firmada y devuelve el cuerpo de la respuesta. Separado para poder probar el cliente. */
@FunctionalInterface
public interface TuyaTransport {
    String exchange(String method, String url, Map<String, String> headers, String body);
}
