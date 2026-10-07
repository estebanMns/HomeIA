package com.home.ia.application.service;

import com.google.ai.client.generativeai.GenerativeModel;
import com.home.ia.domain.model.home.RoomId;
import com.home.ia.infrastructure.persistence.entity.EnergyConsumptionHistoryEntity;
import com.home.ia.infrastructure.persistence.repository.EnergyConsumptionHistoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiOccupancyPredictionServiceTest {

    @Mock
    private GenerativeModel geminiModel;

    @Mock
    private EnergyConsumptionHistoryJpaRepository energyRepository;

    private GeminiOccupancyPredictionService service;

    @BeforeEach
    void setUp() {
        service = new GeminiOccupancyPredictionService(geminiModel, energyRepository);
    }

    @Test
    void shouldReturnDefaultProbabilityWhenNoDataAvailable() {
        UUID roomId = UUID.randomUUID();
        Instant now = Instant.now();

        when(energyRepository.findByDeviceIdAndMeasuredAtAfter(anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        double probability = service.predictOccupancyProbability(RoomId.of(roomId), now);

        assertEquals(0.5, probability, 0.01);
    }

    @Test
    void shouldReturnValidProbabilityBetweenZeroAndOne() {
        UUID roomId = UUID.randomUUID();
        Instant now = Instant.now();

        List<EnergyConsumptionHistoryEntity> mockData = List.of(
                createEnergyData(150.0, now.minusSeconds(3600))
        );

        when(energyRepository.findByDeviceIdAndMeasuredAtAfter(anyString(), anyString()))
                .thenReturn(mockData);

        double probability = service.predictOccupancyProbability(RoomId.of(roomId), now);

        assertTrue(probability >= 0.0 && probability <= 1.0);
    }

    private EnergyConsumptionHistoryEntity createEnergyData(double watts, Instant measuredAt) {
        EnergyConsumptionHistoryEntity entity = new EnergyConsumptionHistoryEntity();
        entity.setConsumptionWatts(BigDecimal.valueOf(watts));
        entity.setMeasuredAt(measuredAt);
        return entity;
    }
}
