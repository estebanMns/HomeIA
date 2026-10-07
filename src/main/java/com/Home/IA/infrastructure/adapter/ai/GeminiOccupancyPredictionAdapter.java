package com.home.ia.infrastructure.adapter.ai;

import com.home.ia.application.port.out.OccupancyPredictionPort;
import com.home.ia.application.service.GeminiOccupancyPredictionService;
import com.home.ia.domain.model.home.RoomId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiOccupancyPredictionAdapter implements OccupancyPredictionPort {

    private final GeminiOccupancyPredictionService predictionService;

    @Override
    public double probabilityOfReturn(RoomId roomId, Instant now) {
        log.debug("Calculating occupancy probability for room: {}", roomId.value());
        try {
            double probability = predictionService.predictOccupancyProbability(roomId, now);
            log.info("Occupancy probability for room {}: {}", roomId.value(), probability);
            return probability;
        } catch (Exception e) {
            log.error("Error calculating occupancy probability for room {}: {}",
                    roomId.value(), e.getMessage(), e);
            return 0.5;
        }
    }
}
