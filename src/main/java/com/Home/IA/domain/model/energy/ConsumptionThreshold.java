package com.home.ia.domain.model.energy;

import java.time.Duration;
import java.util.Objects;

public record ConsumptionThreshold(double maxWatts, Duration sustainedFor) {

    public ConsumptionThreshold {
        if (maxWatts <= 0) {
            throw new IllegalArgumentException("El umbral debe ser mayor que cero");
        }
        Objects.requireNonNull(sustainedFor, "Debe indicarse la duración mínima");
        if (sustainedFor.isNegative()) {
            throw new IllegalArgumentException("La duración no puede ser negativa");
        }
    }

    public boolean isExceededBy(double watts) {
        return watts > maxWatts;
    }
}