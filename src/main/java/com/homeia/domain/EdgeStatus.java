package com.homeia.domain;

public enum EdgeStatus {
    /** Enrolado, aún no se ha conectado. */
    PENDING,
    ONLINE,
    OFFLINE,
    /** Credencial revocada: se ignoran todos sus mensajes. */
    REVOKED
}
