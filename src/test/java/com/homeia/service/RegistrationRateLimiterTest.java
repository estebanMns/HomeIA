package com.homeia.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationRateLimiterTest {

    private static final Instant T0 = Instant.parse("2026-10-19T23:00:00Z");

    @Test
    void permiteCincoRegistrosPorHoraYBloqueaElSexto() {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(Clock.fixed(T0, ZoneOffset.UTC));
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("10.0.0.1"));
        }
        assertFalse(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.2"), "otra IP no se ve afectada");
    }

    @Test
    void seLiberaCuandoPasaLaHora() {
        RegistrationRateLimiter bloqueado = new RegistrationRateLimiter(Clock.fixed(T0, ZoneOffset.UTC));
        for (int i = 0; i < 6; i++) {
            bloqueado.tryAcquire("10.0.0.1");
        }
        assertFalse(bloqueado.tryAcquire("10.0.0.1"));

        // Mismo limitador, una hora y un minuto después
        RegistrationRateLimiterTestClock clock = new RegistrationRateLimiterTestClock(T0);
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(clock);
        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        clock.now = T0.plus(Duration.ofMinutes(61));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
    }

    /** Reloj que se puede adelantar en la prueba. */
    static final class RegistrationRateLimiterTestClock extends Clock {
        Instant now;

        RegistrationRateLimiterTestClock(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
