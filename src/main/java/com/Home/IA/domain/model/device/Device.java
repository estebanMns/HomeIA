package com.home.ia.domain.model.device;

import com.home.ia.domain.exception.DeviceOfflineException;
import com.home.ia.domain.model.home.RoomId;

import java.util.Objects;

public abstract class Device {
    private final DeviceId id;
    private final String name;
    private final RoomId roomId;
    private ConnectionStatus connectionStatus;

    protected Device(DeviceId id, String name, RoomId roomId, ConnectionStatus connectionStatus) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del dispositivo es obligatorio");
        }
        this.id = Objects.requireNonNull(id);
        this.name = name;
        this.roomId = Objects.requireNonNull(roomId);
        this.connectionStatus = Objects.requireNonNull(connectionStatus);
    }

    public abstract DeviceType getType();

    public void markOnline() {
        this.connectionStatus = ConnectionStatus.ONLINE;
    }

    public void markOffline() {
        this.connectionStatus = ConnectionStatus.OFFLINE;
    }

    public boolean isOnline() {
        return connectionStatus == ConnectionStatus.ONLINE;
    }

    protected void ensureOnline() {
        if (!isOnline()) {
            throw new DeviceOfflineException(id);
        }
    }

    public DeviceId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RoomId getRoomId() {
        return roomId;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }
}