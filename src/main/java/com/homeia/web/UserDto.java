package com.homeia.web;

import com.homeia.domain.UserRole;
import com.homeia.persistence.UserEntity;

import java.time.Instant;
import java.util.UUID;

/** Vista pública de un usuario (sin hash de contraseña). */
public record UserDto(UUID id, UUID homeId, String email, String fullName, UserRole role, boolean active,
                      Instant lastLoginAt, Instant createdAt) {

    public static UserDto from(UserEntity user) {
        return new UserDto(user.getId(), user.getHomeId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.isActive(), user.getLastLoginAt(), user.getCreatedAt());
    }
}
