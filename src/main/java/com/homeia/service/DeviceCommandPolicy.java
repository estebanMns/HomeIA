package com.homeia.service;

import com.homeia.domain.DeviceType;

/**
 * Reglas para aceptar un comando manual sobre un dispositivo (ASR-5).
 *
 * - Un sensor de presencia no se enciende ni se apaga (solo admite STATUS).
 * - Solo un administrador puede deshabilitar un tomacorriente.
 * - Apagar un dispositivo crítico (nevera, router, equipo médico) exige confirmación explícita.
 *
 * Es una clase pura: recibe datos simples y devuelve un veredicto, sin lanzar excepciones HTTP.
 */
final class DeviceCommandPolicy {

    enum Outcome { ALLOWED, INVALID, FORBIDDEN, NEEDS_CONFIRMATION }

    record Verdict(Outcome outcome, String message) {
        static final Verdict ALLOWED = new Verdict(Outcome.ALLOWED, null);

        boolean allowed() {
            return outcome == Outcome.ALLOWED;
        }
    }

    private DeviceCommandPolicy() {
    }

    /**
     * @param changesPowerState true para ON / OFF / TOGGLE / BRIGHTNESS; false para STATUS
     * @param turnsOff          true si el comando dejará el dispositivo apagado (OFF, o TOGGLE estando encendido)
     */
    static Verdict evaluate(boolean admin, DeviceType type, boolean critical, String deviceName,
                            boolean changesPowerState, boolean turnsOff, boolean confirmCritical) {
        if (changesPowerState && !type.switchable()) {
            return new Verdict(Outcome.INVALID, "Un sensor de presencia no se puede encender ni apagar");
        }
        if (turnsOff && type == DeviceType.SMART_OUTLET && !admin) {
            return new Verdict(Outcome.FORBIDDEN, "Solo el administrador puede deshabilitar tomacorrientes");
        }
        if (turnsOff && critical && !confirmCritical) {
            return new Verdict(Outcome.NEEDS_CONFIRMATION, deviceName
                    + " es un dispositivo crítico. Confirma que quieres apagarlo (confirmCritical=true)");
        }
        return Verdict.ALLOWED;
    }
}
