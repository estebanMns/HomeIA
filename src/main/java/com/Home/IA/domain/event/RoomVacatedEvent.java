package com.home.ia.domain.event;

import com.home.ia.domain.model.device.DeviceId;
import com.home.ia.domain.model.home.RoomId;

import java.time.Instant;

public record RoomVacatedEvent(RoomId roomId, DeviceId sensorId, Instant occurredAt)
        implements DomainEvent {
}