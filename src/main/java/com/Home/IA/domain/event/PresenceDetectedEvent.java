package com.home.ia.domain.event;

import com.home.ia.domain.model.device.DeviceId;
import com.home.ia.domain.model.home.RoomId;

import java.time.Instant;

public record PresenceDetectedEvent(RoomId roomId, DeviceId sensorId, Instant occurredAt)
        implements DomainEvent {
}