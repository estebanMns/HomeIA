package com.homeia.integration.tuya;

/**
 * Un valor del estado de un dispositivo Tuya ("data point"), por ejemplo {"code":"switch_1","value":true}.
 * value es Boolean, Number o String.
 */
public record TuyaDataPoint(String code, Object value) {
}
