package com.homeia.domain;

/** Por dónde se conecta un dispositivo a HomeIA. */
public enum DeviceProvider {
    /** Dispositivo propio (p. ej. ESP32) que habla el protocolo MQTT de HomeIA a través de un broker. */
    MQTT,
    /** Dispositivo Tuya / Smart Life, controlado a través de la nube de Tuya. */
    TUYA
}
