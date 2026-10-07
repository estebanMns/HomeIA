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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class OccupancyPredictionAdapter implements OccupancyPredictionPort {

    private final EnergyConsumptionHistoryJpaRepository energyRepository;
    private final ConcurrentHashMap<String, Double> roomPredictionCache = new ConcurrentHashMap<>();

    @Override
    public double probabilityOfReturn(RoomId roomId, Instant now) {
        String roomIdStr = roomId.value().toString();

        // Consultar el cache primero
        if (roomPredictionCache.containsKey(roomIdStr)) {
            log.debug("Cache hit for room {}", roomIdStr);
            return roomPredictionCache.get(roomIdStr);
        }

        try {
            Instant pastTwentyFourHours = now.minus(24, ChronoUnit.HOURS);
            List<EnergyConsumptionHistoryEntity> recentConsumption =
                    getEnergyConsumptionForRoom(roomIdStr, pastTwentyFourHours);

            if (recentConsumption.isEmpty()) {
                log.debug("No recent consumption data for room {}, returning default probability", roomIdStr);
                double defaultProbability = 0.5;
                roomPredictionCache.put(roomIdStr, defaultProbability);
                return defaultProbability;
            }

            double averageConsumption = recentConsumption.stream()
                    .mapToDouble(entity -> entity.getConsumptionWatts().doubleValue())
                    .average()
                    .orElse(0.0);

            double probability = calculateProbability(averageConsumption);
            roomPredictionCache.put(roomIdStr, probability);

            log.debug("Predicted occupancy probability for room {}: {}", roomIdStr, probability);
            return probability;
        } catch (Exception e) {
            log.error("Error predicting occupancy for room {}: {}", roomIdStr, e.getMessage());
            return 0.5;
        }
    }

    private List<EnergyConsumptionHistoryEntity> getEnergyConsumptionForRoom(String roomId, Instant afterTime) {
        try {
            return energyRepository.findByDeviceIdAndMeasuredAtAfter(roomId, afterTime);
        } catch (Exception e) {
            log.warn("Failed to retrieve energy consumption data for room {}: {}", roomId, e.getMessage());
            return Collections.emptyList();
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
