package com.homeia.integration.tuya;

/** Rango de un valor numérico de Tuya: el valor real es valor / 10^scale. */
public record TuyaScale(int scale, double min, double max) {
}
