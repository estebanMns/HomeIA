package com.homeia.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Reglas de entrega de WhatsApp: cuántas veces reintentar, cuánto esperar entre intentos y cómo dejar
 * el texto apto para una plantilla de Meta (los parámetros no admiten saltos de línea, tabulaciones
 * ni más de 4 espacios seguidos).
 */
final class WhatsAppDeliveryPolicy {

    static final int MAX_ATTEMPTS = 3;
    /** Espera antes del 2.º y del 3.er intento. */
    private static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5));
    private static final int MAX_PARAM_LENGTH = 1000;

    private WhatsAppDeliveryPolicy() {
    }

    static boolean hasAttemptsLeft(int attemptsMade) {
        return attemptsMade < MAX_ATTEMPTS;
    }

    /** Cuándo hacer el siguiente intento después de {@code attemptsMade} fallos. */
    static Instant nextAttemptAt(int attemptsMade, Instant now) {
        int index = Math.max(0, Math.min(attemptsMade - 1, BACKOFF.size() - 1));
        return now.plus(BACKOFF.get(index));
    }

    static String templateParam(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        String clean = text.replaceAll("[\\r\\n\\t]+", " · ").replaceAll(" {2,}", " ").trim();
        return clean.length() > MAX_PARAM_LENGTH ? clean.substring(0, MAX_PARAM_LENGTH - 1) + "…" : clean;
    }
}
