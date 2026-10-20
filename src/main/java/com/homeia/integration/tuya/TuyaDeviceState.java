package com.homeia.integration.tuya;

import java.util.List;

/** Estado actual de un dispositivo en la nube de Tuya: si está conectado y sus valores. */
public record TuyaDeviceState(boolean online, List<TuyaDataPoint> dataPoints) {
    public TuyaDeviceState {
        dataPoints = List.copyOf(dataPoints);
    }
}
