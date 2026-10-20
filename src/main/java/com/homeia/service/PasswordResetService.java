package com.homeia.service;

import com.homeia.persistence.PasswordResetTokenEntity;
import com.homeia.persistence.PasswordResetTokenRepository;
import com.homeia.persistence.RefreshTokenRepository;
import com.homeia.persistence.UserEntity;
import com.homeia.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Restablecimiento de contraseña por correo.
 * - Si el correo existe, se envía un enlace de un solo uso que caduca (por defecto 30 min).
 * - Si no existe, la respuesta es la misma: no se revela qué correos tienen cuenta.
 * - Al cambiar la contraseña se cierran todas las sesiones abiertas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${homeia.password-reset.minutes:30}")
    private long minutesValid;

    @Value("${homeia.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public void requestReset(String email) {
        String key = email == null ? "" : email.trim().toLowerCase();
        userRepository.findByEmailIgnoreCase(key)
                .filter(UserEntity::isActive)
                .ifPresent(this::issueAndSend);
    }

    private void issueAndSend(UserEntity user) {
        // Un solo enlace vigente: el nuevo reemplaza a los anteriores.
        tokenRepository.deleteAllByUserId(user.getId());

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setUserId(user.getId());
        entity.setTokenHash(AuthService.hash(token));
        entity.setExpiresAt(Instant.now().plus(Duration.ofMinutes(minutesValid)));
        tokenRepository.save(entity);

        String link = frontendUrl + "/auth/reset?token=" + token;
        try {
            emailService.sendPasswordReset(user.getEmail(), user.getFullName(), link, minutesValid);
            auditService.record(user.getHomeId(), user.getEmail(), "PASSWORD_RESET_REQUESTED", "USER",
                    user.getId().toString(), "Enlace enviado al correo registrado");
        } catch (Exception e) {
            log.error("No se pudo enviar el correo de restablecimiento a {}: {}", user.getEmail(), e.getMessage());
        }
    }

    @Transactional
    public int purgeExpiredTokens() {
        return tokenRepository.deleteExpiredOrUsed(Instant.now());
    }

    @Transactional
    public void confirmReset(String token, String newPassword) {
        PasswordResetTokenEntity entity = tokenRepository.findByTokenHash(AuthService.hash(token))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("El enlace no es válido o ya caducó. Solicita uno nuevo"));

        UserEntity user = userRepository.findById(entity.getUserId())
                .filter(UserEntity::isActive)
                .orElseThrow(() -> ApiException.badRequest("El enlace no es válido. Solicita uno nuevo"));
        PasswordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("La nueva contraseña debe ser distinta de la anterior");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        // Borrar todos los enlaces del usuario invalida también este (uso único).
        refreshTokenRepository.deleteAllByUserId(user.getId());
        tokenRepository.deleteAllByUserId(user.getId());
        auditService.record(user.getHomeId(), user.getEmail(), "PASSWORD_RESET_COMPLETED", "USER",
                user.getId().toString(), "Contraseña restablecida por correo");
    }
}
