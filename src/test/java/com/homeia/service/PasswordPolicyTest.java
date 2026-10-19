package com.homeia.service;

import com.homeia.service.PasswordPolicy.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    private static final String NOMBRE = "Esteban Meneses";
    private static final String CORREO = "esteban.meneses@campusucc.edu.co";

    private static List<Rule> fallas(String password) {
        return PasswordPolicy.violations(password, NOMBRE, CORREO);
    }

    @Test
    void unaContrasenaSeguraCumpleTodasLasReglas() {
        assertTrue(fallas("Nevera#Azul-2026").isEmpty());
        assertTrue(fallas("Río Verde 7 llegó!").isEmpty(), "se permiten frases con espacios y tildes");
    }

    @Test
    void exigeLongitudEntre12Y64() {
        assertTrue(fallas("Nv#7aZ").contains(Rule.LENGTH));
        assertTrue(fallas("Nv#7aZ" + "x".repeat(70)).contains(Rule.LENGTH));
    }

    @Test
    void respetaElLimiteDe72BytesDeBcrypt() {
        // 40 caracteres con tilde = más de 72 bytes en UTF-8, aunque sean menos de 64 caracteres
        String larga = "Á1#" + "éñ".repeat(20);
        assertTrue(fallas(larga).contains(Rule.LENGTH));
    }

    @Test
    void exigeMayusculaMinusculaNumeroYSimbolo() {
        assertTrue(fallas("nevera#azul-2026").contains(Rule.UPPERCASE));
        assertTrue(fallas("NEVERA#AZUL-2026").contains(Rule.LOWERCASE));
        assertTrue(fallas("Nevera#Azul-Roja").contains(Rule.DIGIT));
        assertTrue(fallas("NeveraAzul2026xy").contains(Rule.SYMBOL));
    }

    @Test
    void rechazaEspaciosAlInicioOAlFinal() {
        assertTrue(fallas(" Nevera#Azul-2026").contains(Rule.NO_EDGE_SPACES));
        assertTrue(fallas("Nevera#Azul-2026 ").contains(Rule.NO_EDGE_SPACES));
    }

    @Test
    void rechazaRepeticionesYSecuencias() {
        assertTrue(fallas("Neveraaa#Azul-26").contains(Rule.NO_REPEATS));
        assertTrue(fallas("Nevera#Azul-1234").contains(Rule.NO_SEQUENCES));
        assertTrue(fallas("Nevera#Azul-dcba7").contains(Rule.NO_SEQUENCES));
        assertFalse(fallas("Nevera#Azul-1357").contains(Rule.NO_SEQUENCES));
    }

    @Test
    void rechazaElNombreOElCorreoSinImportarMayusculasNiTildes() {
        assertTrue(fallas("ÉSTEBAN#Azul-2026").contains(Rule.NO_PERSONAL_DATA), "nombre con tilde y mayúsculas");
        assertTrue(fallas("Nevera#Meneses-26").contains(Rule.NO_PERSONAL_DATA), "apellido");
        assertFalse(fallas("Nevera#Campusucc7").contains(Rule.NO_PERSONAL_DATA),
                "el dominio del correo no cuenta, solo lo que va antes de la @");
    }

    @Test
    void rechazaPalabrasMuyUsadas() {
        assertTrue(fallas("MiPassword#2026x").contains(Rule.NOT_COMMON));
        assertTrue(fallas("Qwerty#Nevera-26").contains(Rule.NOT_COMMON));
        assertTrue(fallas("Hola#HomeIA-2026").contains(Rule.NOT_COMMON));
    }

    @Test
    void elMensajeDeErrorListaTodasLasReglasIncumplidas() {
        try {
            PasswordPolicy.validate("abc", NOMBRE, CORREO);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("mayúscula"));
            assertTrue(e.getMessage().contains("número"));
            assertTrue(e.getMessage().contains("símbolo"));
            return;
        }
        throw new AssertionError("debió lanzar ApiException");
    }

    @Test
    void contrasenaVaciaONulaFallaSinErrores() {
        assertEquals(List.of(Rule.LENGTH, Rule.UPPERCASE, Rule.LOWERCASE, Rule.DIGIT, Rule.SYMBOL),
                PasswordPolicy.violations(null));
        assertFalse(PasswordPolicy.violations("").isEmpty());
    }
}
