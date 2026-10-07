package com.home.ia.domain.event;

import com.home.ia.domain.model.device.DeviceId;
import com.home.ia.domain.model.home.RoomId;

import java.time.Instant;
import java.util.List;

public record DevicesSwitchedOffAutomaticallyEvent(RoomId roomId,
        List<DeviceId> deviceIds,
        Instant occurredAt) implements DomainEvent {
    public DevicesSwitchedOffAutomaticallyEvent {
        deviceIds = List.copyOf(deviceIds);
    }
}