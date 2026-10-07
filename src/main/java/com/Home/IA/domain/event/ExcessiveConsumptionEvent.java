package com.home.ia.domain.event;

import com.home.ia.domain.model.device.DeviceId;

import java.time.Instant;

public record ExcessiveConsumptionEvent(DeviceId deviceId,
        double averageWatts,
        double thresholdWatts,
        Instant occurredAt) implements DomainEvent {
}