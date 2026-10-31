package com.homeia.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los intentos por dirección IP dentro de una ventana de tiempo fija.
 * Vive en memoria; si hay varias instancias del backend, cada una lleva su propio conteo.
 */
public class RateLimiter {

    private record Window(int count, Instant start) {
    }

    private final int maxPerWindow;
    private final Duration window;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(int maxPerWindow, Duration window) {
        this(maxPerWindow, window, Clock.systemUTC());
    }

    RateLimiter(int maxPerWindow, Duration window, Clock clock) {
        this.maxPerWindow = maxPerWindow;
        this.window = window;
        this.clock = clock;
    }

    /** Cuenta un intento para la IP y devuelve false si ya superó el límite. */
    public boolean tryAcquire(String ip) {
        Instant now = clock.instant();
        Window w = windows.compute(key(ip), (k, current) ->
                current == null || current.start().plus(window).isBefore(now)
                        ? new Window(1, now)
                        : new Window(current.count() + 1, current.start()));
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().start().plus(window).isBefore(now));
        }
        return w.count() <= maxPerWindow;
    }

    /** Olvida los intentos de la IP, p. ej. tras un login correcto: así solo cuentan los fallos seguidos. */
    public void reset(String ip) {
        windows.remove(key(ip));
    }

    private static String key(String ip) {
        return ip == null ? "desconocida" : ip;
    }
}
