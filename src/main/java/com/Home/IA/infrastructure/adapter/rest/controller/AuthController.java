package com.home.ia.infrastructure.adapter.rest.controller;

import com.home.ia.application.service.AuditService;
import com.home.ia.infrastructure.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        log.info("POST /api/auth/login - Intento de login para usuario: {}", request.getEmail());
        try {
            if (request.getEmail() == null || request.getEmail().isEmpty()) {
                log.warn("Intento de login con email vacío");
                auditService.logAuthAction("UNKNOWN", "LOGIN_FAILED", "Email vacío");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Email es requerido")
                                .build());
            }

            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                log.warn("Intento de login con password vacío para email: {}", request.getEmail());
                auditService.logAuthAction("UNKNOWN", "LOGIN_FAILED", "Password vacío");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Password es requerido")
                                .build());
            }

            // TODO: Validar contra base de datos real
            // Por ahora usamos un usuario de prueba
            if ("andrea@homeia.co".equals(request.getEmail()) && "HomeIA2025".equals(request.getPassword())) {
                String userId = "user-001";
                String token = jwtUtil.generateToken(userId, request.getEmail());

                log.info("Login exitoso para usuario: {}", request.getEmail());
                auditService.logAuthAction(userId, "LOGIN_SUCCESS",
                        "Usuario logueado exitosamente desde " + request.getIpAddress());

                return ResponseEntity.ok(LoginResponse.builder()
                        .success(true)
                        .message("Login exitoso")
                        .token(token)
                        .userId(userId)
                        .email(request.getEmail())
                        .expiresIn(86400)
                        .timestamp(Instant.now())
                        .build());
            } else {
                log.warn("Credenciales inválidas para email: {}", request.getEmail());
                auditService.logAuthAction("UNKNOWN", "LOGIN_FAILED",
                        "Credenciales inválidas para: " + request.getEmail());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Email o password inválido")
                                .build());
            }
        } catch (Exception e) {
            log.error("Error durante login: {}", e.getMessage(), e);
            auditService.logAuthAction("UNKNOWN", "LOGIN_ERROR", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message("Error interno del servidor")
                            .build());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@RequestHeader("Authorization") String authHeader) {
        log.info("POST /api/auth/logout - Logout requerido");
        try {
            String token = authHeader != null && authHeader.startsWith("Bearer ")
                    ? authHeader.substring(7)
                    : null;

            if (token == null || !jwtUtil.validateToken(token)) {
                log.warn("Intento de logout con token inválido");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LogoutResponse.builder()
                                .success(false)
                                .message("Token inválido")
                                .build());
            }

            String userId = jwtUtil.extractUserId(token);
            String email = jwtUtil.extractEmail(token);

            log.info("Logout exitoso para usuario: {}", userId);
            auditService.logAuthAction(userId, "LOGOUT", "Usuario deslogueado");

            return ResponseEntity.ok(LogoutResponse.builder()
                    .success(true)
                    .message("Logout exitoso")
                    .userId(userId)
                    .email(email)
                    .timestamp(Instant.now())
                    .build());
        } catch (Exception e) {
            log.error("Error durante logout: {}", e.getMessage(), e);
            auditService.logAuthAction("UNKNOWN", "LOGOUT_ERROR", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LogoutResponse.builder()
                            .success(false)
                            .message("Error interno del servidor")
                            .build());
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        log.debug("POST /api/auth/validate - Validación de token");
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Intento de validación sin token Bearer");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(TokenValidationResponse.builder()
                                .valid(false)
                                .message("Token no proporcionado")
                                .build());
            }

            String token = authHeader.substring(7);

            if (!jwtUtil.validateToken(token)) {
                log.warn("Token inválido o expirado");
                return ResponseEntity.ok(TokenValidationResponse.builder()
                        .valid(false)
                        .message("Token inválido o expirado")
                        .build());
            }

            String userId = jwtUtil.extractUserId(token);
            String email = jwtUtil.extractEmail(token);
            boolean expired = jwtUtil.isTokenExpired(token);

            log.debug("Token validado para usuario: {}", userId);

            return ResponseEntity.ok(TokenValidationResponse.builder()
                    .valid(true)
                    .userId(userId)
                    .email(email)
                    .expired(expired)
                    .message("Token válido")
                    .build());
        } catch (Exception e) {
            log.error("Error validando token: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TokenValidationResponse.builder()
                            .valid(false)
                            .message("Error validando token")
                            .build());
        }
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class LoginRequest {
        private String email;
        private String password;
        private String ipAddress;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class LoginResponse {
        private Boolean success;
        private String message;
        private String token;
        private String userId;
        private String email;
        private Integer expiresIn;
        private Instant timestamp;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class LogoutResponse {
        private Boolean success;
        private String message;
        private String userId;
        private String email;
        private Instant timestamp;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class TokenValidationResponse {
        private Boolean valid;
        private String message;
        private String userId;
        private String email;
        private Boolean expired;
    }
}
