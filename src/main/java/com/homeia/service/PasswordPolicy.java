package com.homeia.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reglas de contraseña de HomeIA. Es la fuente de verdad: el frontend muestra las mismas reglas
 * (homeIafrontend/src/lib/passwordPolicy.ts) para guiar al usuario, pero quien decide es este backend.
 * Si cambias una regla aquí, cámbiala también allá.
 *
 * Se aplica en todos los lugares donde se define una contraseña: registro, configuración inicial,
 * creación y edición de usuarios, cambio de contraseña y restablecimiento.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    /** Tope técnico, no una regla de negocio: BCrypt solo usa los primeros 72 bytes. */
    public static final int MAX_LENGTH = 64;
    static final int MAX_BYTES = 72;

    public enum Rule {
        LENGTH("Mínimo " + MIN_LENGTH + " caracteres"),
        UPPERCASE("Al menos una letra mayúscula"),
        SYMBOL("Al menos un carácter especial, por ejemplo . , # ! - _");

        private final String description;

        Rule(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    private PasswordPolicy() {
    }

    /** Reglas que la contraseña NO cumple (vacío si es válida). */
    public static List<Rule> violations(String password) {
        List<Rule> failed = new ArrayList<>();
        String pw = password == null ? "" : password;

        int length = pw.codePointCount(0, pw.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH || pw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            failed.add(Rule.LENGTH);
        }
        if (pw.codePoints().noneMatch(Character::isUpperCase)) {
            failed.add(Rule.UPPERCASE);
        }
        // Especial = cualquier carácter que no sea letra, número ni espacio: . , # ! - _ @ $ % & * ? etc.
        if (pw.codePoints().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            failed.add(Rule.SYMBOL);
        }
        return failed;
    }

    /** Lanza un 400 con todas las reglas incumplidas, para que el usuario las corrija de una vez. */
    public static void validate(String password) {
        List<Rule> failed = violations(password);
        if (!failed.isEmpty()) {
            StringBuilder message = new StringBuilder("La contraseña no cumple las reglas: ");
            for (int i = 0; i < failed.size(); i++) {
                if (i > 0) message.append("; ");
                message.append(failed.get(i).description().toLowerCase(Locale.ROOT));
            }
            if (failed.contains(Rule.LENGTH) && password != null && password.length() > MAX_LENGTH) {
                message.append(" (máximo ").append(MAX_LENGTH).append(")");
            }
            throw ApiException.badRequest(message.toString());
        }
    }
}
