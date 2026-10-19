package com.homeia.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita la creación de cuentas por dirección IP: como máximo {@value #MAX_PER_WINDOW} intentos por hora.
 * Evita que un script cree cuentas en masa o pruebe correos para saber cuáles existen.
 * Vive en memoria; si hay varias instancias del backend, cada una lleva su propio conteo.
 */
@Component
public class RegistrationRateLimiter {

    static final int MAX_PER_WINDOW = 5;
    static final Duration WINDOW = Duration.ofHours(1);

    private record Window(int count, Instant start) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RegistrationRateLimiter() {
        this(Clock.systemUTC());
    }

    RegistrationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Cuenta un intento para la IP y devuelve false si ya superó el límite. */
    public boolean tryAcquire(String ip) {
        Instant now = clock.instant();
        Window w = windows.compute(ip == null ? "desconocida" : ip, (k, current) ->
                current == null || current.start().plus(WINDOW).isBefore(now)
                        ? new Window(1, now)
                        : new Window(current.count() + 1, current.start()));
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().start().plus(WINDOW).isBefore(now));
        }
        return w.count() <= MAX_PER_WINDOW;
    }
}
