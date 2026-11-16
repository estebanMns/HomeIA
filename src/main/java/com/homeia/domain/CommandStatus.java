package com.homeia.domain;

/** Ciclo de vida de una orden enviada a través de HomeIA Edge. */
public enum CommandStatus {
    /** Creada, aún sin publicar. */
    PENDING,
    /** Publicada; esperando confirmación del Edge. */
    SENT,
    /** El Edge la ejecutó y confirmó. */
    CONFIRMED,
    /** El Edge la intentó y falló (dispositivo inalcanzable, credenciales, etc.). */
    FAILED,
    /** No hubo confirmación antes de expirar. */
    EXPIRED;

    public boolean finished() {
        return this == CONFIRMED || this == FAILED || this == EXPIRED;
    }
}
