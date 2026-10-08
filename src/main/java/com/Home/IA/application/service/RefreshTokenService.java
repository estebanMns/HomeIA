package com.home.ia.application.service;

import com.home.ia.infrastructure.persistence.entity.RefreshTokenEntity;
import com.home.ia.infrastructure.persistence.repository.RefreshTokenJpaRepository;
import com.home.ia.infrastructure.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final AuditService auditService;
    private final JwtUtil jwtUtil;

    @Value("${refresh.token.expire.minutes:10080}")
    private long refreshTokenExpireMinutes;

    @Value("${refresh.token.max.per.user:5}")
    private int maxTokensPerUser;

    public String generateRefreshToken(String userId, String userEmail) {
        try {
            log.info("Generando refresh token para usuario: {}", userId);

            // Verificar límite de tokens por usuario
            long tokenCount = refreshTokenRepository.countValidTokensByUserId(userId);
            if (tokenCount >= maxTokensPerUser) {
                log.warn("Límite de refresh tokens alcanzado para usuario: {}", userId);
                // Revocar el token más antiguo
                revokeOldestValidToken(userId);
            }

            // Generar token seguro
            String token = generateSecureToken();
            Instant expiresAt = Instant.now().plusSeconds(refreshTokenExpireMinutes * 60);

            RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                    .token(token)
                    .userId(userId)
                    .userEmail(userEmail)
                    .expiresAt(expiresAt)
                    .revoked(false)
                    .used(false)
                    .createdAt(Instant.now())
                    .build();

            refreshTokenRepository.save(refreshToken);
            log.info("Refresh token generado exitosamente para usuario: {}", userId);

            auditService.logAuthAction(userId, "REFRESH_TOKEN_GENERATED",
                    "Refresh token generado exitosamente");

            return token;
        } catch (Exception e) {
            log.error("Error generando refresh token: {}", e.getMessage(), e);
            auditService.logAuthAction(userId, "REFRESH_TOKEN_ERROR", "Error: " + e.getMessage());
            throw new RuntimeException("Error generando refresh token", e);
        }
    }

    public Optional<String> refreshAccessToken(String refreshToken) {
        try {
            log.info("Refresh token request recibido");

            Optional<RefreshTokenEntity> tokenOpt = refreshTokenRepository.findByToken(refreshToken);

            if (tokenOpt.isEmpty()) {
                log.warn("Refresh token no encontrado");
                return Optional.empty();
            }

            RefreshTokenEntity refreshTokenEntity = tokenOpt.get();

            if (!refreshTokenEntity.isValid()) {
                log.warn("Refresh token inválido o expirado para usuario: {}", refreshTokenEntity.getUserId());
                auditService.logAuthAction(refreshTokenEntity.getUserId(), "REFRESH_TOKEN_INVALID",
                        "Refresh token inválido o expirado");
                return Optional.empty();
            }

            // Marcar token como usado
            refreshTokenEntity.setUsed(true);
            refreshTokenEntity.setUsedAt(Instant.now());
            refreshTokenRepository.save(refreshTokenEntity);

            // Generar nuevo access token
            String newAccessToken = jwtUtil.generateToken(
                    refreshTokenEntity.getUserId(),
                    refreshTokenEntity.getUserEmail()
            );

            log.info("Access token refrescado exitosamente para usuario: {}", refreshTokenEntity.getUserId());
            auditService.logAuthAction(refreshTokenEntity.getUserId(), "ACCESS_TOKEN_REFRESHED",
                    "Access token refrescado usando refresh token");

            return Optional.of(newAccessToken);
        } catch (Exception e) {
            log.error("Error refrescando access token: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    public void revokeToken(String refreshToken, String userId) {
        try {
            Optional<RefreshTokenEntity> tokenOpt = refreshTokenRepository.findByToken(refreshToken);

            if (tokenOpt.isPresent()) {
                RefreshTokenEntity token = tokenOpt.get();
                token.setRevoked(true);
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);

                log.info("Refresh token revocado para usuario: {}", userId);
                auditService.logAuthAction(userId, "REFRESH_TOKEN_REVOKED", "Refresh token revocado");
            }
        } catch (Exception e) {
            log.error("Error revocando refresh token: {}", e.getMessage(), e);
        }
    }

    public void revokeAllTokens(String userId) {
        try {
            log.info("Revocando todos los refresh tokens para usuario: {}", userId);

            refreshTokenRepository.findByUserIdAndRevokedFalse(userId)
                    .forEach(token -> {
                        token.setRevoked(true);
                        token.setRevokedAt(Instant.now());
                        refreshTokenRepository.save(token);
                    });

            log.info("Todos los refresh tokens revocados para usuario: {}", userId);
            auditService.logAuthAction(userId, "ALL_REFRESH_TOKENS_REVOKED",
                    "Todos los refresh tokens revocados");
        } catch (Exception e) {
            log.error("Error revocando todos los tokens: {}", e.getMessage(), e);
        }
    }

    private void revokeOldestValidToken(String userId) {
        try {
            refreshTokenRepository.findValidTokensByUserId(userId)
                    .stream()
                    .min((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()))
                    .ifPresent(token -> {
                        token.setRevoked(true);
                        token.setRevokedAt(Instant.now());
                        refreshTokenRepository.save(token);
                        log.info("Token más antiguo revocado para usuario: {}", userId);
                    });
        } catch (Exception e) {
            log.error("Error revocando token antiguo: {}", e.getMessage());
        }
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] values = new byte[32];
        random.nextBytes(values);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(values);
    }

    public void cleanupExpiredTokens() {
        try {
            refreshTokenRepository.deleteExpiredTokens();
            log.info("Tokens expirados limpiados");
        } catch (Exception e) {
            log.error("Error limpiando tokens expirados: {}", e.getMessage());
        }
    }
}
