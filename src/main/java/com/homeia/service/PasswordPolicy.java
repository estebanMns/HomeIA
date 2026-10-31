package com.homeia.service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas de contraseña de HomeIA. Es la fuente de verdad: el frontend muestra las mismas reglas
 * (homeIafrontend/src/lib/passwordPolicy.ts) para guiar al usuario, pero quien decide es este backend.
 * Si cambias una regla aquí, cámbiala también allá.
 *
 * Se aplica en todos los lugares donde se define una contraseña: registro, configuración inicial,
 * creación y edición de usuarios, cambio de contraseña y restablecimiento. El login de cuentas
 * existentes no se ve afectado.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    /** Tope técnico, no una regla de negocio: BCrypt solo usa los primeros 72 bytes. */
    public static final int MAX_LENGTH = 64;
    static final int MAX_BYTES = 72;
    /** Largo a partir del cual una secuencia (1234, abcd, qwer) se rechaza. */
    static final int SEQUENCE_LENGTH = 4;
    /** Largo mínimo de una parte del nombre o del correo para considerarla dato personal. */
    static final int PERSONAL_TOKEN_LENGTH = 4;

    /** Alfabeto, números y filas del teclado: una contraseña no puede contener 4 seguidos, en ningún sentido. */
    static final List<String> SEQUENCES = List.of(
            "abcdefghijklmnopqrstuvwxyz", "0123456789", "1234567890", "qwertyuiop", "asdfghjklñ", "zxcvbnm");

    /**
     * Contraseñas y palabras base muy usadas. Se comparan con la contraseña en minúsculas, sin tildes
     * y sin números ni símbolos: así "Password2026!" o "Qwerty#123" se reconocen como comunes.
     */
    static final Set<String> COMMON = Set.of(
            "password", "contrasena", "clave", "qwerty", "admin", "administrador", "homeia", "usuario", "user",
            "login", "bienvenido", "welcome", "letmein", "iloveyou", "teamo", "secreto", "secret", "master",
            "dragon", "monkey", "football", "futbol", "princesa", "superman", "batman", "colombia", "abc",
            "casa", "micasa", "hogar", "energia", "test", "prueba", "default", "changeme", "cambiar");

    /** Palabras que no pueden aparecer en ninguna parte de la contraseña, aunque vayan acompañadas. */
    static final Set<String> FORBIDDEN_ROOTS = Set.of("password", "contrasena", "qwerty", "homeia");

    private static final Pattern REPEATED = Pattern.compile("(.)\\1\\1");
    private static final Pattern NOT_LETTER = Pattern.compile("[^\\p{L}]");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    public enum Rule {
        LENGTH("Entre " + MIN_LENGTH + " y " + MAX_LENGTH + " caracteres"),
        LOWERCASE("Al menos una letra minúscula"),
        UPPERCASE("Al menos una letra mayúscula"),
        DIGIT("Al menos un número"),
        SYMBOL("Al menos un carácter especial, por ejemplo . , # ! - _"),
        NO_SPACES("Sin espacios"),
        NO_REPEATS("Sin 3 caracteres iguales seguidos (aaa, 111)"),
        NO_SEQUENCES("Sin secuencias de " + SEQUENCE_LENGTH + " o más (1234, abcd, qwer)"),
        NOT_COMMON("No puede ser una contraseña común (password, qwerty, admin…)"),
        NO_PERSONAL_DATA("No puede contener tu nombre ni tu correo");

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

    /** Reglas que la contraseña NO cumple (vacío si es válida), sin revisar datos personales. */
    public static List<Rule> violations(String password) {
        return violations(password, null, null);
    }

    /** Reglas que la contraseña NO cumple (vacío si es válida). El correo y el nombre pueden ser null. */
    public static List<Rule> violations(String password, String email, String fullName) {
        List<Rule> failed = new ArrayList<>();
        String pw = password == null ? "" : password;
        String folded = fold(pw);

        int length = pw.codePointCount(0, pw.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH || pw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            failed.add(Rule.LENGTH);
        }
        if (pw.codePoints().noneMatch(Character::isLowerCase)) {
            failed.add(Rule.LOWERCASE);
        }
        if (pw.codePoints().noneMatch(Character::isUpperCase)) {
            failed.add(Rule.UPPERCASE);
        }
        if (pw.codePoints().noneMatch(Character::isDigit)) {
            failed.add(Rule.DIGIT);
        }
        // Especial = cualquier carácter que no sea letra, número ni espacio: . , # ! - _ @ $ % & * ? etc.
        if (pw.codePoints().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            failed.add(Rule.SYMBOL);
        }
        if (pw.codePoints().anyMatch(Character::isWhitespace)) {
            failed.add(Rule.NO_SPACES);
        }
        if (REPEATED.matcher(pw.toLowerCase(Locale.ROOT)).find()) {
            failed.add(Rule.NO_REPEATS);
        }
        if (hasSequence(pw.toLowerCase(Locale.ROOT))) {
            failed.add(Rule.NO_SEQUENCES);
        }
        if (isCommon(folded)) {
            failed.add(Rule.NOT_COMMON);
        }
        if (containsPersonalData(folded, email, fullName)) {
            failed.add(Rule.NO_PERSONAL_DATA);
        }
        return failed;
    }

    /** Lanza un 400 con todas las reglas incumplidas, sin revisar datos personales. */
    public static void validate(String password) {
        validate(password, null, null);
    }

    /** Lanza un 400 con todas las reglas incumplidas, para que el usuario las corrija de una vez. */
    public static void validate(String password, String email, String fullName) {
        List<Rule> failed = violations(password, email, fullName);
        if (!failed.isEmpty()) {
            StringBuilder message = new StringBuilder("La contraseña no cumple las reglas: ");
            for (int i = 0; i < failed.size(); i++) {
                if (i > 0) message.append("; ");
                message.append(failed.get(i).description().toLowerCase(Locale.ROOT));
            }
            throw ApiException.badRequest(message.toString());
        }
    }

    /** Minúsculas y sin tildes: "Contraseña" → "contrasena". */
    static String fold(String value) {
        return DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean hasSequence(String lower) {
        for (String sequence : SEQUENCES) {
            String reversed = new StringBuilder(sequence).reverse().toString();
            for (int i = 0; i + SEQUENCE_LENGTH <= sequence.length(); i++) {
                if (lower.contains(sequence.substring(i, i + SEQUENCE_LENGTH))
                        || lower.contains(reversed.substring(i, i + SEQUENCE_LENGTH))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCommon(String folded) {
        String letters = NOT_LETTER.matcher(folded).replaceAll("");
        return COMMON.contains(letters) || FORBIDDEN_ROOTS.stream().anyMatch(letters::contains);
    }

    /** Cada parte del nombre y del correo (antes de la @) con 4 o más caracteres cuenta como dato personal. */
    private static boolean containsPersonalData(String folded, String email, String fullName) {
        List<String> tokens = new ArrayList<>();
        if (email != null) {
            int at = email.indexOf('@');
            tokens.addAll(List.of(TOKEN_SEPARATOR.split(fold(at >= 0 ? email.substring(0, at) : email))));
        }
        if (fullName != null) {
            tokens.addAll(List.of(TOKEN_SEPARATOR.split(fold(fullName))));
        }
        return tokens.stream().anyMatch(t -> t.length() >= PERSONAL_TOKEN_LENGTH && folded.contains(t));
    }
}
