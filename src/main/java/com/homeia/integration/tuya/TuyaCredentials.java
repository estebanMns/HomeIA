package com.homeia.integration.tuya;

/** Limpieza de las credenciales de Tuya leídas del .env. */
final class TuyaCredentials {

    private TuyaCredentials() {
    }

    /**
     * Quita espacios y comillas alrededor del valor. En el .env, TUYA_ACCESS_ID="abc" llega con las comillas
     * incluidas y Tuya lo rechaza con el error 2009 (clientId is invalid).
     */
    static String clean(String value) {
        String v = value == null ? "" : value.trim();
        while (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            v = v.substring(1, v.length() - 1).trim();
        }
        return v;
    }
}
