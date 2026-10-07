package com.home.ia.domain.model.energy;

import com.home.ia.domain.model.device.DeviceId;

import java.time.Instant;
import java.util.Objects;

public record EnergyReading(DeviceId deviceId, Instant measuredAt, double powerWatts) {

    public EnergyReading {
        Objects.requireNonNull(deviceId, "La lectura debe pertenecer a un dispositivo");
        Objects.requireNonNull(measuredAt, "La lectura debe tener fecha");
        if (powerWatts < 0) {
            throw new IllegalArgumentException("La potencia medida no puede ser negativa");
        }
    }

    public boolean exceeds(ConsumptionThreshold threshold) {
        return threshold.isExceededBy(powerWatts);
    }
}