package com.homeia.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Reglas del modo de emparejamiento Zigbee (permit_join): siempre con tiempo límite y con verificación de que
 * el coordinador realmente lo cerró. Si el Edge sigue informando permit_join abierto después del plazo
 * (más un margen), se fuerza el cierre y se alerta.
 */
public final class PermitJoinPolicy {

    public static final int MAX_SECONDS = 120;
    public static final Duration GRACE = Duration.ofSeconds(20);

    public enum Verdict { OK, FORCE_CLOSE }

    private PermitJoinPolicy() {
    }

    public static int validateSeconds(int seconds) {
        if (seconds < 1 || seconds > MAX_SECONDS) {
            throw new IllegalArgumentException("El tiempo de emparejamiento debe estar entre 1 y " + MAX_SECONDS + " segundos");
        }
        return seconds;
    }

    /**
     * @param reportedOpen lo que informa el último heartbeat del Edge
     * @param allowedUntil hasta cuándo HomeIA autorizó el emparejamiento (null = nunca se autorizó)
     */
    public static Verdict evaluate(boolean reportedOpen, Instant allowedUntil, Instant now) {
        if (!reportedOpen) return Verdict.OK;
        if (allowedUntil == null) return Verdict.FORCE_CLOSE; // abierto sin autorización de HomeIA
        return now.isAfter(allowedUntil.plus(GRACE)) ? Verdict.FORCE_CLOSE : Verdict.OK;
    }
}
