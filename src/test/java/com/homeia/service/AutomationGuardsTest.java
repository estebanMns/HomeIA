package com.homeia.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationGuardsTest {

    private static final int ESPERA = 15;

    @Test
    void apagaUnaHabitacionVaciaQueSuperoElTiempoConfigurado() {
        assertTrue(AutomationGuards.canSwitchOff(true, false, true, false, 20, ESPERA));
        assertTrue(AutomationGuards.canSwitchOff(true, false, true, false, 15, ESPERA));
    }

    @Test
    void laIaNoApagaUnaHabitacionVaciaHaceMenosDelTiempoConfigurado() {
        assertFalse(AutomationGuards.canSwitchOff(true, false, true, false, 1, ESPERA));
        assertFalse(AutomationGuards.canSwitchOff(true, false, true, false, 14, ESPERA));
    }

    @Test
    void nuncaApagaCriticosNiHabitacionesOcupadasNiSinDatos() {
        assertFalse(AutomationGuards.canSwitchOff(true, true, true, false, 120, ESPERA), "crítico");
        assertFalse(AutomationGuards.canSwitchOff(true, false, true, true, 120, ESPERA), "ocupada");
        assertFalse(AutomationGuards.canSwitchOff(true, false, false, false, 120, ESPERA), "sin datos de presencia");
        assertFalse(AutomationGuards.canSwitchOff(false, false, true, false, 120, ESPERA), "ya apagado");
    }

    @Test
    void enciendeSoloConPresenciaConfirmada() {
        assertTrue(AutomationGuards.canSwitchOn(false, true, true));
        assertFalse(AutomationGuards.canSwitchOn(false, true, false), "habitación vacía");
        assertFalse(AutomationGuards.canSwitchOn(false, false, false), "sin datos");
        assertFalse(AutomationGuards.canSwitchOn(true, true, true), "ya encendido");
    }
}
