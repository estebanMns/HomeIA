package com.homeia.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

/**
 * Caché de la probabilidad de regreso por habitación.
 *
 * Guarda y devuelve SIEMPRE el valor en porcentaje (0-100). Antes la caché guardaba 0-1 y el método
 * devolvía 0-100 solo al recalcular, así que una lectura desde caché (p. ej. 0.8) se comparaba contra
 * la sensibilidad (70) y la regla "es probable que regrese" nunca se cumplía.
 *
 * Es segura para hilos: el scheduler y las peticiones HTTP ("Ejecutar ahora") la usan a la vez.
 */
final class ReturnProbabilityCache {

    private record Entry(Instant computedAt, double percent) {
    }

    private final Duration ttl;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    ReturnProbabilityCache(Duration ttl) {
        this.ttl = Objects.requireNonNull(ttl);
    }

    /**
     * Devuelve el porcentaje vigente de la habitación o lo calcula con {@code probability},
     * que debe devolver un valor entre 0 y 1 (los valores fuera de rango se acotan).
     */
    double percent(UUID roomId, Instant now, DoubleSupplier probability) {
        Entry cached = entries.get(roomId);
        if (cached != null && cached.computedAt().plus(ttl).isAfter(now)) {
            return cached.percent();
        }
        double percent = toPercent(probability.getAsDouble());
        entries.put(roomId, new Entry(now, percent));
        return percent;
    }

    static double toPercent(double probability) {
        if (Double.isNaN(probability)) {
            return 0;
        }
        return Math.max(0, Math.min(1, probability)) * 100.0;
    }
}
