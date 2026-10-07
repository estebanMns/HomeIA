package com.home.ia.infrastructure.adapter.ai;

import com.home.ia.application.port.out.OccupancyPredictionPort;
import com.home.ia.domain.model.home.RoomId;
import com.home.ia.infrastructure.persistence.entity.EnergyConsumptionHistoryEntity;
import com.home.ia.infrastructure.persistence.repository.EnergyConsumptionHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OccupancyPredictionAdapter implements OccupancyPredictionPort {

    private final EnergyConsumptionHistoryJpaRepository energyRepository;
    private final Map<String, Double> roomPredictionCache = new HashMap<>();

    @Override
    public double probabilityOfReturn(RoomId roomId, Instant now) {
        String roomIdStr = roomId.value().toString();

        try {
            Instant pastTwentyFourHours = now.minus(24, ChronoUnit.HOURS);
            List<EnergyConsumptionHistoryEntity> recentConsumption = energyRepository
                    .findByDeviceIdAndMeasuredAtAfter(roomIdStr, pastTwentyFourHours);

            if (recentConsumption.isEmpty()) {
                log.debug("No recent consumption data for room {}, returning 0.5", roomIdStr);
                return 0.5;
            }

            double averageConsumption = recentConsumption.stream()
                    .mapToDouble(EnergyConsumptionHistoryEntity::getConsumptionWatts)
                    .average()
                    .orElse(0.0);

            double probability = calculateProbability(averageConsumption);
            roomPredictionCache.put(roomIdStr, probability);

            log.debug("Predicted occupancy probability for room {}: {}", roomIdStr, probability);
            return probability;
        } catch (Exception e) {
            log.error("Error predicting occupancy for room {}", roomIdStr, e);
            return 0.0;
        }
    }

    private double calculateProbability(double averageConsumption) {
        if (averageConsumption < 50) {
            return 0.1;
        } else if (averageConsumption < 200) {
            return 0.3;
        } else if (averageConsumption < 500) {
            return 0.7;
        } else {
            return 0.95;
        }
    }
}
