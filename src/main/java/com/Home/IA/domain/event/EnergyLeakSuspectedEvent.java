package com.home.ia.domain.event;

import com.home.ia.domain.model.device.DeviceId;

import java.time.Instant;

public record EnergyLeakSuspectedEvent(DeviceId deviceId,
        double anomalyScore,
        Instant occurredAt) implements DomainEvent {
}