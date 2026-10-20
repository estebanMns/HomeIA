package com.homeia.service;

import com.homeia.domain.UserRole;
import com.homeia.persistence.RefreshTokenRepository;
import com.homeia.persistence.UserEntity;
import com.homeia.persistence.UserRepository;
import com.homeia.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<UserEntity> list(UUID homeId) {
        return userRepository.findByHomeIdOrderByFullNameAsc(homeId);
    }

    @Transactional(readOnly = true)
    public UserEntity get(UUID homeId, UUID userId) {
        return userRepository.findByIdAndHomeId(userId, homeId).orElseThrow(() -> ApiException.notFound("Usuario"));
    }

    @Transactional
    public UserEntity create(AuthPrincipal principal, String email, String password, String fullName, UserRole role) {
        String cleanEmail = normalizeEmail(email);
        if (fullName == null || fullName.isBlank()) {
            throw ApiException.badRequest("El nombre es obligatorio");
        }
        PasswordPolicy.validate(password);
        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw ApiException.conflict("Ya existe un usuario con ese correo");
        }
        UserEntity user = new UserEntity();
        user.setHomeId(principal.homeId());
        user.setEmail(cleanEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName.trim());
        user.setRole(role == null ? UserRole.MEMBER : role);
        user.setActive(true);
        userRepository.save(user);
        auditService.record(principal, "USER_CREATED", "USER", user.getId().toString(),
                cleanEmail + " (" + user.getRole() + ")");
        return user;
    }

    @Transactional
    public UserEntity update(AuthPrincipal principal, UUID userId, String fullName, UserRole role,
                             Boolean active, String password) {
        UserEntity user = get(principal.homeId(), userId);
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        if (role != null && role != user.getRole()) {
            if (userId.equals(principal.userId())) {
                throw ApiException.badRequest("No puedes cambiar tu propio rol");
            }
            if (user.getRole() == UserRole.ADMIN) {
                ensureAnotherAdmin(principal.homeId(), userId);
            }
            user.setRole(role);
        }
        if (active != null && active != user.isActive()) {
            if (userId.equals(principal.userId()) && !active) {
                throw ApiException.badRequest("No puedes desactivar tu propia cuenta");
            }
            if (!active) {
                ensureAnotherAdmin(principal.homeId(), userId);
                refreshTokenRepository.deleteAllByUserId(userId);
            }
            user.setActive(active);
        }
        if (password != null && !password.isBlank()) {
            PasswordPolicy.validate(password);
            user.setPasswordHash(passwordEncoder.encode(password));
            refreshTokenRepository.deleteAllByUserId(userId);
        }
        user.setUpdatedAt(Instant.now());
        auditService.record(principal, "USER_UPDATED", "USER", userId.toString(), user.getEmail());
        return user;
    }

    /** Cambio de contraseña propio. Pide la actual y cierra las sesiones abiertas en otros dispositivos. */
    @Transactional
    public void changeOwnPassword(AuthPrincipal principal, String currentPassword, String newPassword) {
        UserEntity user = get(principal.homeId(), principal.userId());
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.unauthorized("La contraseña actual no es correcta");
        }
        PasswordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("La nueva contraseña debe ser distinta de la actual");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        refreshTokenRepository.deleteAllByUserId(user.getId());
        auditService.record(principal, "PASSWORD_CHANGED", "USER", user.getId().toString(), user.getEmail());
    }

    @Transactional
    public void delete(AuthPrincipal principal, UUID userId) {
        if (userId.equals(principal.userId())) {
            throw ApiException.badRequest("No puedes eliminar tu propia cuenta");
        }
        UserEntity user = get(principal.homeId(), userId);
        if (user.getRole() == UserRole.ADMIN) {
            ensureAnotherAdmin(principal.homeId(), userId);
        }
        userRepository.delete(user);
        auditService.record(principal, "USER_DELETED", "USER", userId.toString(), user.getEmail());
    }

    private void ensureAnotherAdmin(UUID homeId, UUID userId) {
        boolean another = userRepository.findByHomeIdOrderByFullNameAsc(homeId).stream()
                .anyMatch(u -> u.isActive() && u.getRole() == UserRole.ADMIN && !u.getId().equals(userId));
        if (!another) {
            throw ApiException.badRequest("Debe quedar al menos un administrador activo en la casa");
        }
    }

    static String normalizeEmail(String email) {
        if (email == null || !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiException.badRequest("Correo electrónico inválido");
        }
        return email.trim().toLowerCase();
    }

}
