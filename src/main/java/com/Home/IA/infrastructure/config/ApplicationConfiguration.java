package com.home.ia.infrastructure.config;

import com.home.ia.application.port.out.*;
import com.home.ia.application.service.SwitchOffVacantRoomsService;
import com.home.ia.domain.policy.OccupancyPolicy;
import com.home.ia.domain.policy.OutletProtectionPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class ApplicationConfiguration {

    @Value("${app.automation.occupancy-threshold-minutes:15}")
    private Integer occupancyThresholdMinutes;

    @Value("${app.automation.occupancy-probability-threshold:0.7}")
    private Double occupancyProbabilityThreshold;

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public OccupancyPolicy occupancyPolicy() {
        return new OccupancyPolicy(
            Duration.ofMinutes(occupancyThresholdMinutes),
            occupancyProbabilityThreshold
        );
    }

    @Bean
    public OutletProtectionPolicy outletProtectionPolicy() {
        return new OutletProtectionPolicy();
    }

    @Bean
    public SwitchOffVacantRoomsService switchOffVacantRoomsService(
            DeviceRepositoryPort deviceRepository,
            DeviceCommandPort deviceCommand,
            OccupancyPredictionPort occupancyPrediction,
            DomainEventPublisherPort eventPublisher,
            OccupancyPolicy occupancyPolicy,
            OutletProtectionPolicy outletProtectionPolicy,
            Clock clock) {
        return new SwitchOffVacantRoomsService(
            deviceRepository,
            deviceCommand,
            occupancyPrediction,
            eventPublisher,
            occupancyPolicy,
            outletProtectionPolicy,
            clock
        );
    }
}
