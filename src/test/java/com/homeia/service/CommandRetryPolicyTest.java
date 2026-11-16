package com.homeia.service;

import com.homeia.service.CommandRetryPolicy.Decision;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandRetryPolicyTest {

    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant expires = t0.plus(CommandRetryPolicy.TTL);

    @Test
    void esperaLaConfirmacionAntesDeReintentar() {
        assertEquals(Decision.WAIT, CommandRetryPolicy.decide(1, t0, expires, t0.plusSeconds(3)));
        assertEquals(Decision.RESEND, CommandRetryPolicy.decide(1, t0, expires, t0.plusSeconds(9)));
    }

    @Test
    void expiraAlAgotarIntentosOTiempo() {
        assertEquals(Decision.EXPIRE, CommandRetryPolicy.decide(3, t0, expires, t0.plusSeconds(9)));
        assertEquals(Decision.EXPIRE, CommandRetryPolicy.decide(1, t0, expires, expires));
        assertEquals(Decision.RESEND, CommandRetryPolicy.decide(0, null, expires, t0));
    }
}
