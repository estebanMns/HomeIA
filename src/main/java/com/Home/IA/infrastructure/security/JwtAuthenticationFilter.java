package com.home.ia.infrastructure.security;

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

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("Request sin token JWT: {} {}", request.getMethod(), request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);

            if (!jwtUtil.validateToken(token)) {
                log.warn("Token JWT inválido en request: {} {}", request.getMethod(), request.getRequestURI());
                auditService.logAuthAction("UNKNOWN", "TOKEN_INVALID",
                        "Token inválido en: " + request.getRequestURI());
                filterChain.doFilter(request, response);
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
            filterChain.doFilter(request, response);
        }
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
