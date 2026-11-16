package com.homeia.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private CorsConfiguration corsFor(String allowedOrigins) {
        HomeIaProperties properties = new HomeIaProperties(null, new HomeIaProperties.Cors(allowedOrigins),
                null, null, null, null, null, null);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        return new SecurityConfig().corsConfigurationSource(properties).getCorsConfiguration(request);
    }

    @Test
    void aceptaOrigenesExactosYConComodin() {
        CorsConfiguration cors = corsFor(" https://home-ia-ten.vercel.app , https://home-ia-*-esteban3345s-projects.vercel.app");

        assertThat(cors.checkOrigin("https://home-ia-ten.vercel.app")).isEqualTo("https://home-ia-ten.vercel.app");
        assertThat(cors.checkOrigin("https://home-ia-git-main-esteban3345s-projects.vercel.app")).isNotNull();
    }

    @Test
    void rechazaOrigenesNoListados() {
        CorsConfiguration cors = corsFor("https://home-ia-ten.vercel.app");

        assertThat(cors.checkOrigin("https://otra-app.vercel.app")).isNull();
        assertThat(cors.checkOrigin("http://localhost:3000")).isNull();
    }
}
