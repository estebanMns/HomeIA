package com.homeia.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private static final Instant T0 = Instant.parse("2026-10-19T23:00:00Z");

    @Test
    void permiteCincoIntentosPorHoraYBloqueaElSexto() {
        RateLimiter limiter = new RateLimiter(5, Duration.ofHours(1), Clock.fixed(T0, ZoneOffset.UTC));
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("10.0.0.1"));
        }
        assertFalse(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.2"), "otra IP no se ve afectada");
    }

    @Test
    void seLiberaCuandoPasaLaVentana() {
        TestClock clock = new TestClock(T0);
        RateLimiter limiter = new RateLimiter(5, Duration.ofHours(1), clock);
        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        assertFalse(limiter.tryAcquire("10.0.0.1"));
        clock.now = T0.plus(Duration.ofMinutes(61));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
    }

    @Test
    void resetOlvidaLosIntentosDeEsaIp() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(15), Clock.fixed(T0, ZoneOffset.UTC));
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");
        assertFalse(limiter.tryAcquire("10.0.0.1"));
        limiter.reset("10.0.0.1");
        assertTrue(limiter.tryAcquire("10.0.0.1"), "tras un login correcto vuelve a empezar el conteo");
    }

    /** Reloj que se puede adelantar en la prueba. */
    static final class TestClock extends Clock {
        Instant now;

        TestClock(Instant now) {
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
