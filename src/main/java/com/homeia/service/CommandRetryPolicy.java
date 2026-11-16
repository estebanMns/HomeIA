package com.homeia.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Decide qué hacer con una orden que el Edge aún no confirma. Los reintentos reutilizan el mismo requestId:
 * el Edge descarta duplicados y responde con el resultado ya conocido, así reenviar es idempotente.
 */
public final class CommandRetryPolicy {

    public enum Decision { WAIT, RESEND, EXPIRE }

    public static final int MAX_ATTEMPTS = 3;
    public static final Duration ACK_TIMEOUT = Duration.ofSeconds(8);
    public static final Duration TTL = Duration.ofSeconds(45);

    private CommandRetryPolicy() {
    }

    public static Decision decide(int attempts, Instant lastSentAt, Instant expiresAt, Instant now) {
        if (!now.isBefore(expiresAt)) return Decision.EXPIRE;
        if (lastSentAt == null) return Decision.RESEND;
        if (now.isBefore(lastSentAt.plus(ACK_TIMEOUT))) return Decision.WAIT;
        return attempts >= MAX_ATTEMPTS ? Decision.EXPIRE : Decision.RESEND;
    }
}
