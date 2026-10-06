package com.home.ia.domain.model.device;

import java.util.Objects;
import java.util.UUID;

public record DeviceId(UUID value) {
    public DeviceId {
        Objects.requireNonNull(value, "El id del dispositivo es obligatorio");
    }

    public static DeviceId generate() {
        return new DeviceId(UUID.randomUUID());
    }
}