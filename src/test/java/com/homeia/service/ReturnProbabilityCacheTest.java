package com.homeia.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReturnProbabilityCacheTest {

    private static final Instant NOW = Instant.parse("2026-10-18T15:00:00Z");
    private static final UUID ROOM = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void laLecturaDesdeCacheDevuelveElMismoPorcentajeQueElCalculo() {
        ReturnProbabilityCache cache = new ReturnProbabilityCache(Duration.ofMinutes(10));

        double primera = cache.percent(ROOM, NOW, () -> 0.8);
        double desdeCache = cache.percent(ROOM, NOW.plusSeconds(60), () -> 0.1);

        // Antes del arreglo la segunda lectura devolvía 0.8 en vez de 80.0
        assertEquals(80.0, primera, 1e-9);
        assertEquals(80.0, desdeCache, 1e-9);
    }

    @Test
    void noRecalculaMientrasLaEntradaEstaVigente() {
        ReturnProbabilityCache cache = new ReturnProbabilityCache(Duration.ofMinutes(10));
        AtomicInteger llamadas = new AtomicInteger();

        cache.percent(ROOM, NOW, () -> { llamadas.incrementAndGet(); return 0.5; });
        cache.percent(ROOM, NOW.plus(Duration.ofMinutes(9)), () -> { llamadas.incrementAndGet(); return 0.5; });

        assertEquals(1, llamadas.get());
    }

    @Test
    void recalculaCuandoVenceLaEntrada() {
        ReturnProbabilityCache cache = new ReturnProbabilityCache(Duration.ofMinutes(10));

        cache.percent(ROOM, NOW, () -> 0.2);
        double recalculada = cache.percent(ROOM, NOW.plus(Duration.ofMinutes(11)), () -> 0.9);

        assertEquals(90.0, recalculada, 1e-9);
    }

    @Test
    void acotaValoresFueraDeRango() {
        assertEquals(100.0, ReturnProbabilityCache.toPercent(1.7), 1e-9);
        assertEquals(0.0, ReturnProbabilityCache.toPercent(-0.3), 1e-9);
        assertEquals(0.0, ReturnProbabilityCache.toPercent(Double.NaN), 1e-9);
    }
}
