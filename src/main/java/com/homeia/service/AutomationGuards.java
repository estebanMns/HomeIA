package com.homeia.service;

/**
 * Guardarraíles que toda decisión automática (de la IA o de las reglas) debe cumplir antes de
 * enviar un comando. Son reglas puras, sin Spring ni base de datos, para poder probarlas solas.
 */
final class AutomationGuards {

    private AutomationGuards() {
    }

    /**
     * Se puede apagar automáticamente solo si: está encendido, no es crítico, la habitación tiene datos
     * de presencia, está vacía y lleva vacía al menos el tiempo configurado.
     * Antes, las decisiones de la IA no validaban el tiempo y podían apagar una habitación vacía hace 1 minuto.
     */
    static boolean canSwitchOff(boolean deviceOn, boolean critical, boolean presenceKnown, boolean roomOccupied,
                                long vacantMinutes, int requiredVacancyMinutes) {
        return deviceOn
                && !critical
                && presenceKnown
                && !roomOccupied
                && vacantMinutes >= requiredVacancyMinutes;
    }

    /** Se puede encender automáticamente solo si está apagado y hay presencia confirmada en la habitación. */
    static boolean canSwitchOn(boolean deviceOn, boolean presenceKnown, boolean roomOccupied) {
        return !deviceOn && presenceKnown && roomOccupied;
    }
}
