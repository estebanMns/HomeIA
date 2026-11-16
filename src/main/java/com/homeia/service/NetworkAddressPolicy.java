package com.homeia.service;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Las direcciones LAN que se guardan para Shelly/Tasmota/WLED/ESPHome solo pueden ser IPv4 privadas literales
 * (sin nombres DNS, puertos ni rutas). Así el Edge nunca se convierte en un proxy hacia Internet ni hacia
 * servicios de la propia máquina (SSRF). El Edge vuelve a validar antes de conectarse.
 */
public final class NetworkAddressPolicy {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern ZIGBEE_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private NetworkAddressPolicy() {
    }

    /** Vacío si la dirección es una IPv4 privada válida; si no, el motivo. */
    public static Optional<String> checkPrivateIpv4(String address) {
        if (address == null || address.isBlank()) return Optional.of("La dirección IP es obligatoria");
        var m = IPV4.matcher(address.trim());
        if (!m.matches()) return Optional.of("La dirección debe ser una IPv4 literal (p. ej. 192.168.1.40)");
        int[] o = new int[4];
        for (int i = 0; i < 4; i++) {
            String part = m.group(i + 1);
            if (part.length() > 1 && part.startsWith("0")) return Optional.of("La dirección IP no puede tener ceros a la izquierda");
            o[i] = Integer.parseInt(part);
            if (o[i] > 255) return Optional.of("La dirección IP no es válida");
        }
        boolean isPrivate = o[0] == 10
                || (o[0] == 172 && o[1] >= 16 && o[1] <= 31)
                || (o[0] == 192 && o[1] == 168);
        if (!isPrivate) return Optional.of("Solo se permiten direcciones de la red local (10.x, 172.16-31.x, 192.168.x)");
        if (o[3] == 0 || o[3] == 255) return Optional.of("La dirección corresponde a una red o a un broadcast");
        return Optional.empty();
    }

    /** friendly_name de Zigbee2MQTT: se usa dentro de un tópico MQTT, así que no admite '/', '+' ni '#'. */
    public static Optional<String> checkZigbeeName(String name) {
        if (name == null || !ZIGBEE_NAME.matcher(name).matches()) {
            return Optional.of("El friendly_name Zigbee solo admite letras, números, '_' y '-' (máx. 64)");
        }
        return Optional.empty();
    }
}
