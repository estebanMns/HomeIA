package com.homeia.service;

import com.homeia.service.PasswordPolicy.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void cumpleConOchoCaracteresUnaMayusculaYUnCaracterEspecial() {
        assertTrue(PasswordPolicy.violations("Casa.123").isEmpty());
        assertTrue(PasswordPolicy.violations("Nevera,azul").isEmpty(), "la coma cuenta como especial");
        assertTrue(PasswordPolicy.violations("Mi casa ahorra!").isEmpty(), "se permiten espacios");
    }

    @Test
    void exigeMinimoOchoCaracteres() {
        assertEquals(List.of(Rule.LENGTH), PasswordPolicy.violations("Casa.12"));
        assertTrue(PasswordPolicy.violations("Casa.123").isEmpty());
    }

    @Test
    void exigeUnaMayuscula() {
        assertEquals(List.of(Rule.UPPERCASE), PasswordPolicy.violations("casa.1234"));
        assertTrue(PasswordPolicy.violations("Ñandú.rápido").isEmpty(), "mayúsculas con tilde o eñe también cuentan");
    }

    @Test
    void exigeUnCaracterEspecialYElPuntoOLaComaSirven() {
        assertEquals(List.of(Rule.SYMBOL), PasswordPolicy.violations("Casa12345"));
        assertEquals(List.of(Rule.SYMBOL), PasswordPolicy.violations("Casa 12345"), "un espacio no es carácter especial");
        assertTrue(PasswordPolicy.violations("Casa.12345").isEmpty());
        assertTrue(PasswordPolicy.violations("Casa,12345").isEmpty());
        assertTrue(PasswordPolicy.violations("Casa#12345").isEmpty());
    }

    @Test
    void yaNoExigeMinusculasNumerosNiOtrasReglas() {
        assertTrue(PasswordPolicy.violations("CASA.AZUL").isEmpty(), "sin minúsculas");
        assertTrue(PasswordPolicy.violations("Casa.azul").isEmpty(), "sin números");
        assertTrue(PasswordPolicy.violations("Aaaa.1234").isEmpty(), "repeticiones y secuencias permitidas");
    }

    @Test
    void respetaElTopeTecnicoDeBcrypt() {
        assertEquals(List.of(Rule.LENGTH), PasswordPolicy.violations("A." + "x".repeat(70)));
        assertEquals(List.of(Rule.LENGTH), PasswordPolicy.violations("Á." + "é".repeat(40)), "más de 72 bytes");
    }

    @Test
    void elMensajeDeErrorListaTodasLasReglasIncumplidas() {
        try {
            PasswordPolicy.validate("abc");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("mínimo 8 caracteres"));
            assertTrue(e.getMessage().contains("mayúscula"));
            assertTrue(e.getMessage().contains("carácter especial"));
            return;
        }
        throw new AssertionError("debió lanzar ApiException");
    }

    @Test
    void contrasenaNulaFallaSinErrores() {
        assertEquals(List.of(Rule.LENGTH, Rule.UPPERCASE, Rule.SYMBOL), PasswordPolicy.violations(null));
    }
}
