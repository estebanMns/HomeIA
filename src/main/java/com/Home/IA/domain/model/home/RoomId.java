package com.home.ia.domain.model.home;

import java.util.Objects;
import java.util.UUID;

public record RoomId(UUID value) {
    public RoomId {
        Objects.requireNonNull(value, "El id de la habitación es obligatorio");
    }

    public static RoomId generate() {
        return new RoomId(UUID.randomUUID());
    }
}
