package com.homeia.service;

import com.homeia.persistence.RefreshTokenEntity;
import com.homeia.persistence.RefreshTokenRepository;
import com.homeia.persistence.UserEntity;
import com.homeia.persistence.UserRepository;
import com.homeia.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final com.homeia.config.HomeIaProperties properties;
    private final LoginAttemptTracker failedAttempts;

    public record Session(String accessToken, String refreshToken, long expiresInSeconds, UserEntity user) {
    }

    @Transactional
    public Session login(String email, String password) {
        String key = email.trim().toLowerCase();
        if (failedAttempts.isLocked(key)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Espera 15 minutos e inténtalo de nuevo");
        }
        UserEntity user = userRepository.findByEmailIgnoreCase(key)
                .filter(UserEntity::isActive)
                .orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            failedAttempts.registerFailure(key);
            if (user != null) {
                auditService.record(user.getHomeId(), user.getEmail(), "LOGIN_FAILED", "USER", user.getId().toString(),
                        "Contraseña incorrecta");
            }
            throw ApiException.unauthorized("Correo o contraseña incorrectos");
        }
        failedAttempts.clear(key);
        user.setLastLoginAt(Instant.now());
        auditService.record(user.getHomeId(), user.getEmail(), "LOGIN", "USER", user.getId().toString(), null);
        return newSession(user);
    }

    /** Rota el refresh token: el anterior queda revocado y se emite uno nuevo. */
    @Transactional
    public Session refresh(String refreshToken) {
        RefreshTokenEntity stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.unauthorized("Sesión expirada, vuelve a iniciar sesión"));
        UserEntity user = userRepository.findById(stored.getUserId())
                .filter(UserEntity::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Usuario inactivo"));
        stored.setRevoked(true);
        return newSession(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken)).ifPresent(t -> t.setRevoked(true));
    }

    /** Borra refresh tokens vencidos o revocados. Lo ejecuta el scheduler cada noche. */
    @Transactional
    public int purgeExpiredTokens() {
        return refreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
    }

    private Session newSession(UserEntity user) {
        String refresh = randomToken();
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUserId(user.getId());
        entity.setTokenHash(hash(refresh));
        entity.setRevoked(false);
        entity.setExpiresAt(Instant.now().plus(Duration.ofMinutes(properties.jwt().refreshTokenMinutes())));
        refreshTokenRepository.save(entity);
        return new Session(jwtService.createAccessToken(user), refresh, jwtService.accessTokenSeconds(), user);
    }

    private static String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
