package com.homeia.domain;

/** Eventos del historial de un dispositivo. */
public enum DeviceEventType {
    DISCOVERED, IMPORTED, UPDATED, CREDENTIALS_CHANGED, ONLINE, OFFLINE,
    COMMAND_SENT, COMMAND_CONFIRMED, COMMAND_FAILED, COMMAND_EXPIRED,
    ZIGBEE_JOINED, ZIGBEE_LEFT, ERROR
}
