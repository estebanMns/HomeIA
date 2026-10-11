package com.homeia.domain;

public enum ActionType {
    /** La IA apagó dispositivos de una habitación vacía. */
    SWITCHED_OFF_VACANT_ROOM,
    /** Un usuario ejecutó el apagado manualmente desde la API. */
    MANUAL_SHUTDOWN,
    /** La IA decidió mantener encendido un dispositivo crítico. */
    PROTECTED_CRITICAL_DEVICE,
    /** La IA decidió no apagar porque es probable que alguien regrese. */
    KEPT_ON_LIKELY_RETURN
}
