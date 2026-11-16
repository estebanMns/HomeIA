package com.homeia.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PermitJoinPolicyTest {

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void limitaLaDuracion() {
        assertEquals(60, PermitJoinPolicy.validateSeconds(60));
        assertThrows(IllegalArgumentException.class, () -> PermitJoinPolicy.validateSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> PermitJoinPolicy.validateSeconds(121));
    }

    @Test
    void fuerzaElCierreSiSigueAbiertoFueraDePlazoOSinAutorizacion() {
        assertEquals(PermitJoinPolicy.Verdict.OK, PermitJoinPolicy.evaluate(false, null, now));
        assertEquals(PermitJoinPolicy.Verdict.OK, PermitJoinPolicy.evaluate(true, now.plusSeconds(30), now));
        assertEquals(PermitJoinPolicy.Verdict.OK, PermitJoinPolicy.evaluate(true, now.minusSeconds(10), now));
        assertEquals(PermitJoinPolicy.Verdict.FORCE_CLOSE, PermitJoinPolicy.evaluate(true, now.minusSeconds(30), now));
        assertEquals(PermitJoinPolicy.Verdict.FORCE_CLOSE, PermitJoinPolicy.evaluate(true, null, now));
    }
}
