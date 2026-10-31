package com.homeia.service;

import org.springframework.stereotype.Component;

import java.time.Duration;

/** Límites por IP de los endpoints públicos de autenticación, para frenar ataques automatizados. */
@Component
public class AuthRateLimits {

    /** Creación masiva de cuentas o prueba de correos para saber cuáles existen. */
    private final RateLimiter registration = new RateLimiter(5, Duration.ofHours(1));
    /** Fuerza bruta de contraseñas. Un login correcto reinicia el conteo de esa IP. */
    private final RateLimiter login = new RateLimiter(10, Duration.ofMinutes(15));
    /** Envío masivo de correos de recuperación. */
    private final RateLimiter passwordReset = new RateLimiter(5, Duration.ofHours(1));

    public RateLimiter registration() {
        return registration;
    }

    public RateLimiter login() {
        return login;
    }

    public RateLimiter passwordReset() {
        return passwordReset;
    }
}
