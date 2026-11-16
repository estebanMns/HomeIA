package com.homeia.domain;

public enum AlertType {
    /** Consumo por encima del umbral configurado. */
    EXCESSIVE_CONSUMPTION,
    /** Dispositivo encendido durante mucho tiempo en una habitación sin presencia. */
    POSSIBLE_ENERGY_LEAK,
    /** Dispositivo sin conexión. */
    DEVICE_OFFLINE,
    /** Apagado automático ejecutado por la IA. */
    AUTOMATIC_SHUTDOWN,
    /** Alerta creada manualmente por un usuario. */
    MANUAL,
    /** HomeIA Edge dejó de enviar heartbeat. */
    EDGE_OFFLINE,
    /** Emparejamiento Zigbee abierto, dispositivo nuevo unido o cierre no verificado. */
    ZIGBEE_PAIRING,
    /** Evento de seguridad: firma inválida, credencial revocada en uso, etc. */
    SECURITY
}
