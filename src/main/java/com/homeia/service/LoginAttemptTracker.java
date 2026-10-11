package com.homeia.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los intentos fallidos de login por correo: 5 fallos bloquean el correo durante 15 minutos.
 * Vive en memoria; si hay varias instancias del backend, cada una lleva su propio conteo.
 */
@Component
public class LoginAttemptTracker {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempts(int count, Instant windowStart) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String key) {
        Attempts a = attempts.get(key);
        if (a == null) return false;
        if (a.windowStart().plus(WINDOW).isBefore(Instant.now())) {
            attempts.remove(key);
            return false;
        }
        return a.count() >= MAX_FAILURES;
    }

    public void registerFailure(String key) {
        Instant now = Instant.now();
        attempts.compute(key, (k, a) -> {
            if (a == null || a.windowStart().plus(WINDOW).isBefore(now)) {
                return new Attempts(1, now);
            }
            return new Attempts(a.count() + 1, a.windowStart());
        });
    }

    public void clear(String key) {
        attempts.remove(key);
    }
}
