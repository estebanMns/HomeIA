package com.home.ia.domain.model.home;

import java.util.Objects;
import java.util.UUID;

public record HomeId(UUID value) {
    public HomeId {
        Objects.requireNonNull(value, "El id de la casa es obligatorio");
    }

    public static HomeId generate() {
        return new HomeId(UUID.randomUUID());
    }
}