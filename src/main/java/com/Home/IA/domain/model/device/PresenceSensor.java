package com.home.ia.domain.model.device;

import com.home.ia.domain.model.home.RoomId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class PresenceSensor extends Device {
    private boolean occupied;
    private Instant lastChangeAt;

    public PresenceSensor(DeviceId id, String name, RoomId roomId,
            ConnectionStatus connectionStatus,
            boolean occupied, Instant lastChangeAt) {
        super(id, name, roomId, connectionStatus);
        this.occupied = occupied;
        this.lastChangeAt = lastChangeAt;
    }

    public static PresenceSensor register(String name, RoomId roomId) {
        return new PresenceSensor(DeviceId.generate(), name, roomId,
                ConnectionStatus.OFFLINE, false, null);
    }

    public void registerPresence(Instant at) {
        ensureOnline();
        this.occupied = true;
        this.lastChangeAt = Objects.requireNonNull(at);
    }

    public void registerVacancy(Instant at) {
        ensureOnline();
        this.occupied = false;
        this.lastChangeAt = Objects.requireNonNull(at);
    }

    /**
     * Si nunca ha reportado, devuelve cero: ante la duda, no apagamos nada.
     */
    public Duration timeWithoutPresence(Instant now) {
        if (occupied || lastChangeAt == null) {
            return Duration.ZERO;
        }
        return Duration.between(lastChangeAt, now);
    }

    public boolean isOccupied() {
        return occupied;
    }

    public Instant getLastChangeAt() {
        return lastChangeAt;
    }

    @Override
    public DeviceType getType() {
        return DeviceType.PRESENCE_SENSOR;
    }
}