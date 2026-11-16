package com.homeia.edge;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ReplayGuardTest {

    private final ReplayGuard guard = new ReplayGuard(Duration.ofSeconds(120),
            Clock.fixed(Instant.ofEpochSecond(10_000), ZoneOffset.UTC));

    @Test
    void aceptaUnaVezCadaNonce() {
        assertTrue(guard.accept("e1", 10_000, "n1"));
        assertFalse(guard.accept("e1", 10_000, "n1"));
        assertTrue(guard.accept("e2", 10_000, "n1"));
    }

    @Test
    void rechazaMensajesFueraDeLaVentana() {
        assertFalse(guard.accept("e1", 10_000 - 121, "a"));
        assertFalse(guard.accept("e1", 10_000 + 121, "b"));
        assertTrue(guard.accept("e1", 10_000 - 119, "c"));
        assertFalse(guard.accept("e1", 10_000, ""));
    }
}
