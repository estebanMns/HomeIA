package com.home.ia.domain.policy;

import com.home.ia.domain.event.ExcessiveConsumptionEvent;
import com.home.ia.domain.model.device.DeviceId;
import com.home.ia.domain.model.energy.ConsumptionThreshold;
import com.home.ia.domain.model.energy.EnergyReading;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ExcessiveConsumptionPolicy {

    public Optional<ExcessiveConsumptionEvent> evaluate(DeviceId deviceId,
            List<EnergyReading> recentReadings,
            ConsumptionThreshold threshold) {
        if (recentReadings == null || recentReadings.isEmpty()) {
            return Optional.empty();
        }

        List<EnergyReading> sorted = recentReadings.stream()
                .sorted(Comparator.comparing(EnergyReading::measuredAt))
                .toList();

        boolean allAboveThreshold = sorted.stream().allMatch(r -> r.exceeds(threshold));
        if (!allAboveThreshold) {
            return Optional.empty();
        }

        EnergyReading first = sorted.get(0);
        EnergyReading last = sorted.get(sorted.size() - 1);
        Duration sustained = Duration.between(first.measuredAt(), last.measuredAt());
        if (sustained.compareTo(threshold.sustainedFor()) < 0) {
            return Optional.empty();
        }

        double averageWatts = sorted.stream()
                .mapToDouble(EnergyReading::powerWatts)
                .average()
                .orElse(0);

        return Optional.of(new ExcessiveConsumptionEvent(
                deviceId, averageWatts, threshold.maxWatts(), last.measuredAt()));
    }
}