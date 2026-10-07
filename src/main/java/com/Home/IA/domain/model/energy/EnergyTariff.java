package com.home.ia.domain.model.energy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Objects;

public record EnergyTariff(BigDecimal pricePerKwh, String currency) {

    public EnergyTariff {
        Objects.requireNonNull(pricePerKwh, "La tarifa es obligatoria");
        if (pricePerKwh.signum() <= 0) {
            throw new IllegalArgumentException("La tarifa debe ser mayor que cero");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("La moneda es obligatoria");
        }
    }

    public BigDecimal costOf(double watts, Duration duration) {
        double kwh = (watts / 1000.0) * (duration.toSeconds() / 3600.0);
        return pricePerKwh.multiply(BigDecimal.valueOf(kwh))
                .setScale(2, RoundingMode.HALF_UP);
    }
}