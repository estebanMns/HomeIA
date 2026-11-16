package com.homeia.domain;

/** Proveedor o adaptador responsable de comunicarse con el dispositivo. */
public enum DeviceProvider {
    MQTT, TUYA, ZIGBEE, SHELLY, TASMOTA, ESPHOME
}
