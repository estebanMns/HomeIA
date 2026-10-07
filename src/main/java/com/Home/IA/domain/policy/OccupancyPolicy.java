package com.home.ia.domain.policy;

import com.home.ia.domain.model.device.PresenceSensor;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class OccupancyPolicy {
    private final Duration vacancyTimeout;
    private final double returnProbabilityThreshold;

    public OccupancyPolicy(Duration vacancyTimeout, double returnProbabilityThreshold) {
        Objects.requireNonNull(vacancyTimeout);
        if (vacancyTimeout.isZero() || vacancyTimeout.isNegative()) {
            throw new IllegalArgumentException("El tiempo de espera debe ser positivo");
        }
        if (returnProbabilityThreshold < 0 || returnProbabilityThreshold > 1) {
            throw new IllegalArgumentException("La probabilidad debe estar entre 0 y 1");
        }
        this.vacancyTimeout = vacancyTimeout;
        this.returnProbabilityThreshold = returnProbabilityThreshold;
    }

    public boolean shouldSwitchOffRoom(PresenceSensor sensor, Instant now,
            double probabilityOfReturn) {
        if (!sensor.isOnline()) {
            return false; // sin datos confiables, no actuamos
        }

        Duration vacantFor = sensor.timeWithoutPresence(now);
        if (vacantFor.compareTo(vacancyTimeout) < 0) {
            return false;
        }

        boolean likelyToReturn = probabilityOfReturn >= returnProbabilityThreshold;
        boolean withinGracePeriod = vacantFor.compareTo(vacancyTimeout.multipliedBy(2)) < 0;

        return !(likelyToReturn && withinGracePeriod);
    }
}