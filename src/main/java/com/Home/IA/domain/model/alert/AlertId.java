package com.home.ia.domain.model.alert;

import java.util.Objects;
import java.util.UUID;

public record AlertId(UUID value) {
    public AlertId {
        Objects.requireNonNull(value, "El id de la alerta es obligatorio");
    }

    public static AlertId generate() {
        return new AlertId(UUID.randomUUID());
    }
}