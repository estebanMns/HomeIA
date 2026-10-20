package com.homeia.integration.tuya;

/** Dispositivo tal como lo devuelve la nube de Tuya. */
public record TuyaCloudDevice(String id, String name, String category, String productName, boolean online) {
}
