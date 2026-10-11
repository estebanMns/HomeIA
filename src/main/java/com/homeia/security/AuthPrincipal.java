package com.homeia.security;

import com.homeia.domain.UserRole;

import java.util.UUID;

/** Usuario autenticado. La casa (homeId) limita todos los datos que puede ver o modificar. */
public record AuthPrincipal(UUID userId, UUID homeId, String email, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
