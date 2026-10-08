package com.home.ia.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.home.ia.application.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            String path = request.getRequestURI();

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("Request sin token JWT: {} {}", request.getMethod(), path);
                if (requiresAuthentication(path)) {
                    log.warn("Acceso denegado - token no proporcionado: {} {}", request.getMethod(), path);
                    sendUnauthorizedResponse(response, "Token no proporcionado");
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);

            if (!jwtUtil.validateToken(token)) {
                log.warn("Token JWT inválido: {} {}", request.getMethod(), path);
                auditService.logAuthAction("UNKNOWN", "TOKEN_INVALID", "Token inválido en: " + path);
                sendUnauthorizedResponse(response, "Token inválido o expirado");
                return;
            }

            String userId = jwtUtil.extractUserId(token);
            String email = jwtUtil.extractEmail(token);

            if (userId != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                authentication.setDetails(email);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Usuario autenticado: {} ({})", userId, email);
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("Error en JWT filter: {}", e.getMessage(), e);
            auditService.logAuthAction("UNKNOWN", "AUTH_FILTER_ERROR", "Error: " + e.getMessage());
            sendUnauthorizedResponse(response, "Error de autenticación");
        }
    }

    private boolean requiresAuthentication(String path) {
        return !path.startsWith("/api/auth/") &&
               !path.startsWith("/swagger-ui") &&
               !path.startsWith("/v3/api-docs") &&
               !path.startsWith("/h2-console");
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", 401);
        errorResponse.put("message", message);
        errorResponse.put("error", "Unauthorized");

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/h2-console");
    }
}
