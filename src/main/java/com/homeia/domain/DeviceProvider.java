package com.homeia.domain;

/** Proveedor o adaptador responsable de comunicarse con el dispositivo. */
public enum DeviceProvider {
    /** Dispositivo propio (p. ej. ESP32) que habla el protocolo MQTT de HomeIA directamente con el broker. */
    MQTT,
    /** Tuya / Smart Life por la nube del fabricante. */
    TUYA,
    /** Zigbee2MQTT a través de HomeIA Edge. */
    ZIGBEE,
    /** Shelly Gen1 y Gen2/Plus por HTTP local, a través de HomeIA Edge. */
    SHELLY,
    /** Tasmota por HTTP local, a través de HomeIA Edge. */
    TASMOTA,
    /** ESPHome por su API nativa (puerto 6053), a través de HomeIA Edge. */
    ESPHOME,
    /** WLED por su API JSON local, a través de HomeIA Edge. */
    WLED;

    /** Proveedores que se controlan dentro de la LAN mediante un HomeIA Edge enrolado. */
    public boolean viaEdge() {
        return this == ZIGBEE || this == SHELLY || this == TASMOTA || this == ESPHOME || this == WLED;
    }

    /** Proveedores cuya dirección es una IPv4 de la LAN (Zigbee usa friendly_name). */
    public boolean usesIpAddress() {
        return this == SHELLY || this == TASMOTA || this == ESPHOME || this == WLED;
    }
}
