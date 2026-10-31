package com.homeia.service;

import com.homeia.service.PasswordPolicy.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void aceptaUnaContrasenaSegura() {
        assertTrue(PasswordPolicy.violations("Hx7#maceta-Luna92").isEmpty());
        assertTrue(PasswordPolicy.violations("Ñandú.rápido.48").isEmpty(), "tildes y eñe cuentan como letras");
        assertTrue(PasswordPolicy.violations("Prueba#Segura2026x").isEmpty());
    }

    @Test
    void exigeEntreDoceYSesentaYCuatroCaracteres() {
        assertEquals(List.of(Rule.LENGTH), PasswordPolicy.violations("Hx7#maceta1"));
        assertTrue(PasswordPolicy.violations("Hx7#maceta12").isEmpty());
        assertTrue(PasswordPolicy.violations("Hx7#" + "maceta".repeat(11)).contains(Rule.LENGTH), "más de 64");
        assertTrue(PasswordPolicy.violations("Á.7" + "é".repeat(40)).contains(Rule.LENGTH), "más de 72 bytes");
    }

    @Test
    void exigeMinusculaMayusculaNumeroYCaracterEspecial() {
        assertEquals(List.of(Rule.LOWERCASE), PasswordPolicy.violations("HX7#MACETA-LUNA"));
        assertEquals(List.of(Rule.UPPERCASE), PasswordPolicy.violations("hx7#maceta-luna"));
        assertEquals(List.of(Rule.DIGIT), PasswordPolicy.violations("Hx#maceta-Luna"));
        assertEquals(List.of(Rule.SYMBOL), PasswordPolicy.violations("Hx7macetaLuna9"));
    }

    @Test
    void rechazaEspacios() {
        assertEquals(List.of(Rule.NO_SPACES), PasswordPolicy.violations("Hx7#maceta Luna"));
    }

    @Test
    void rechazaTresCaracteresIgualesSeguidos() {
        assertEquals(List.of(Rule.NO_REPEATS), PasswordPolicy.violations("Hx7#macetaaa-Lu"));
        assertEquals(List.of(Rule.NO_REPEATS), PasswordPolicy.violations("Hx777#maceta-Lu"));
        assertEquals(List.of(Rule.NO_REPEATS), PasswordPolicy.violations("Hx7#macetaAa-Lu"), "sin importar mayúsculas");
        assertTrue(PasswordPolicy.violations("Hx77#maceta-Lu").isEmpty(), "dos iguales sí se permiten");
    }

    @Test
    void rechazaSecuenciasDeCuatro() {
        assertEquals(List.of(Rule.NO_SEQUENCES), PasswordPolicy.violations("Hx#maceta-1234"));
        assertEquals(List.of(Rule.NO_SEQUENCES), PasswordPolicy.violations("Hx7#Abcd-maceta"));
        assertEquals(List.of(Rule.NO_SEQUENCES), PasswordPolicy.violations("Hx#maceta-9876"), "también descendentes");
        assertEquals(List.of(Rule.NO_SEQUENCES), PasswordPolicy.violations("Hx7#Qwer-maceta"), "filas del teclado");
        assertTrue(PasswordPolicy.violations("Hx7#maceta-123").isEmpty(), "tres seguidos sí se permiten");
    }

    @Test
    void rechazaContrasenasComunesAunqueLleveNumerosOSimbolos() {
        assertTrue(PasswordPolicy.violations("Password2026!").contains(Rule.NOT_COMMON));
        assertTrue(PasswordPolicy.violations("Contraseña#2026").contains(Rule.NOT_COMMON), "sin importar tildes");
        assertFalse(PasswordPolicy.violations("Admin#2026.xy").contains(Rule.NOT_COMMON),
                "admin solo se rechaza si es la palabra completa");
        assertTrue(PasswordPolicy.violations("Admin#20269").contains(Rule.NOT_COMMON));
        assertTrue(PasswordPolicy.violations("MiPassword#2026x").contains(Rule.NOT_COMMON), "password nunca se permite");
    }

    @Test
    void rechazaElNombreOElCorreoDelUsuario() {
        assertEquals(List.of(Rule.NO_PERSONAL_DATA),
                PasswordPolicy.violations("Esteban#2026xy", "zlesteban@gmail.com", "Esteban Meneses"));
        assertEquals(List.of(Rule.NO_PERSONAL_DATA),
                PasswordPolicy.violations("Hx7#Meneses-Lu", "otro@gmail.com", "Esteban Meneses"));
        assertEquals(List.of(Rule.NO_PERSONAL_DATA),
                PasswordPolicy.violations("Hx7#zlesteban-L", "zlesteban@gmail.com", null), "parte del correo");
        assertTrue(PasswordPolicy.violations("Hx7#maceta-Luna92", "zlesteban@gmail.com", "Esteban Meneses").isEmpty());
        assertTrue(PasswordPolicy.violations("Esteban#2026xy").isEmpty(), "sin datos personales no se revisa");
    }

    @Test
    void elMensajeDeErrorListaTodasLasReglasIncumplidas() {
        ApiException e = assertThrows(ApiException.class, () -> PasswordPolicy.validate("abc"));
        assertTrue(e.getMessage().contains("entre 12 y 64 caracteres"));
        assertTrue(e.getMessage().contains("mayúscula"));
        assertTrue(e.getMessage().contains("número"));
        assertTrue(e.getMessage().contains("carácter especial"));
        assertTrue(e.getMessage().contains("contraseña común"));
    }

    @Test
    void contrasenaNulaFallaSinErrores() {
        List<Rule> failed = PasswordPolicy.violations(null);
        assertTrue(failed.containsAll(List.of(Rule.LENGTH, Rule.LOWERCASE, Rule.UPPERCASE, Rule.DIGIT, Rule.SYMBOL)));
    }
}
