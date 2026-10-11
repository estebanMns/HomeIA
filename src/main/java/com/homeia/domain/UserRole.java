package com.homeia.domain;

public enum UserRole {
    /** Administra la casa: usuarios, configuración de automatización y WhatsApp. */
    ADMIN,
    /** Consulta y controla dispositivos, no administra usuarios. */
    MEMBER
}
