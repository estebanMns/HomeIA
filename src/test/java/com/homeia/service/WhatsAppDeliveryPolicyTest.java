package com.homeia.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppDeliveryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-18T20:00:00Z");

    @Test
    void permiteComoMaximoTresIntentos() {
        assertTrue(WhatsAppDeliveryPolicy.hasAttemptsLeft(1));
        assertTrue(WhatsAppDeliveryPolicy.hasAttemptsLeft(2));
        assertFalse(WhatsAppDeliveryPolicy.hasAttemptsLeft(3));
    }

    @Test
    void esperaCrecienteEntreIntentos() {
        assertEquals(NOW.plus(Duration.ofMinutes(1)), WhatsAppDeliveryPolicy.nextAttemptAt(1, NOW));
        assertEquals(NOW.plus(Duration.ofMinutes(5)), WhatsAppDeliveryPolicy.nextAttemptAt(2, NOW));
    }

    @Test
    void limpiaLosParametrosDeLaPlantilla() {
        // Meta rechaza variables con saltos de línea, tabulaciones o más de 4 espacios seguidos
        assertEquals("Calentador baño · 1500 W ahora",
                WhatsAppDeliveryPolicy.templateParam("Calentador baño\n\t1500 W      ahora"));
        assertEquals("-", WhatsAppDeliveryPolicy.templateParam("  "));
        assertEquals(1000, WhatsAppDeliveryPolicy.templateParam("x".repeat(5000)).length());
    }
}
