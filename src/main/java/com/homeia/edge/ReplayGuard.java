package com.homeia.edge;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rechaza mensajes de Edge viejos o repetidos: la marca de tiempo debe estar dentro de la ventana y cada
 * nonce se acepta una sola vez mientras dure esa ventana. Así un mensaje capturado no se puede reinyectar.
 */
public class ReplayGuard {

    private final Duration window;
    private final Clock clock;
    private final Map<String, Long> seen = new ConcurrentHashMap<>();

    public ReplayGuard(Duration window) {
        this(window, Clock.systemUTC());
    }

    public ReplayGuard(Duration window, Clock clock) {
        this.window = window;
        this.clock = clock;
    }

    /** true si el mensaje es nuevo y reciente. Registra el nonce. */
    public boolean accept(String edgeId, long tsEpochSeconds, String nonce) {
        if (nonce == null || nonce.isBlank() || nonce.length() > 64) return false;
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - tsEpochSeconds) > window.toSeconds()) return false;
        if (seen.size() > 50_000) {
            seen.entrySet().removeIf(e -> e.getValue() < now - window.toSeconds());
        }
        return seen.putIfAbsent(edgeId + "|" + nonce, tsEpochSeconds) == null;
    }
}
