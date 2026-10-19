package com.homeia.service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas de contraseña de HomeIA. Es la fuente de verdad: el frontend muestra las mismas reglas
 * (homeIafrontend/src/lib/passwordPolicy.ts) para guiar al usuario, pero quien decide es este backend.
 * Si cambias una regla aquí, cámbiala también allá.
 *
 * Se aplica en todos los lugares donde se define una contraseña: registro, configuración inicial,
 * creación y edición de usuarios, cambio de contraseña y restablecimiento.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;
    /** BCrypt solo usa los primeros 72 bytes; más allá, dos contraseñas distintas darían el mismo hash. */
    static final int MAX_BYTES = 72;

    /** Palabras y secuencias que aparecen en las contraseñas más filtradas, más las propias de la app. */
    private static final Set<String> COMMON_FRAGMENTS = Set.of(
            "password", "passw0rd", "contrasena", "contrasenia", "clave", "qwerty", "asdfgh", "zxcvbn",
            "123456", "654321", "111111", "000000", "abc123", "iloveyou", "teamo", "letmein", "welcome",
            "bienvenido", "admin", "administrador", "homeia", "colombia", "football", "futbol", "dragon",
            "monkey", "master", "secret", "secreto", "changeme", "cambiame");

    public enum Rule {
        LENGTH("Entre " + MIN_LENGTH + " y " + MAX_LENGTH + " caracteres"),
        UPPERCASE("Al menos una letra mayúscula"),
        LOWERCASE("Al menos una letra minúscula"),
        DIGIT("Al menos un número"),
        SYMBOL("Al menos un símbolo, por ejemplo ! ? # $ % & * - _"),
        NO_EDGE_SPACES("Sin espacios al inicio ni al final"),
        NO_REPEATS("Sin el mismo carácter 3 veces seguidas (aaa, 111)"),
        NO_SEQUENCES("Sin secuencias de 4 caracteres (1234, abcd, dcba)"),
        NO_PERSONAL_DATA("Sin tu nombre ni tu correo"),
        NOT_COMMON("Sin palabras muy usadas (password, qwerty, 123456, homeia…)");

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

    /**
     * Reglas que la contraseña NO cumple (vacío si es válida).
     *
     * @param personalData datos de la persona que no deben aparecer en la contraseña: nombre completo y correo
     */
    public static List<Rule> violations(String password, String... personalData) {
        List<Rule> failed = new ArrayList<>();
        String pw = password == null ? "" : password;

        int length = pw.codePointCount(0, pw.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH || pw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            failed.add(Rule.LENGTH);
        }
        if (pw.codePoints().noneMatch(Character::isUpperCase)) {
            failed.add(Rule.UPPERCASE);
        }
        if (pw.codePoints().noneMatch(Character::isLowerCase)) {
            failed.add(Rule.LOWERCASE);
        }
        if (pw.codePoints().noneMatch(Character::isDigit)) {
            failed.add(Rule.DIGIT);
        }
        if (pw.codePoints().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            failed.add(Rule.SYMBOL);
        }
        if (!pw.isEmpty() && (Character.isWhitespace(pw.charAt(0)) || Character.isWhitespace(pw.charAt(pw.length() - 1)))) {
            failed.add(Rule.NO_EDGE_SPACES);
        }
        if (hasRepeats(pw)) {
            failed.add(Rule.NO_REPEATS);
        }
        String folded = fold(pw);
        if (hasSequence(folded)) {
            failed.add(Rule.NO_SEQUENCES);
        }
        if (containsPersonalData(folded, personalData)) {
            failed.add(Rule.NO_PERSONAL_DATA);
        }
        String lettersAndDigits = folded.replaceAll("[^a-z0-9]", "");
        if (COMMON_FRAGMENTS.stream().anyMatch(lettersAndDigits::contains)) {
            failed.add(Rule.NOT_COMMON);
        }
        return failed;
    }

    /** Lanza un 400 con todas las reglas incumplidas, para que el usuario las corrija de una vez. */
    public static void validate(String password, String... personalData) {
        List<Rule> failed = violations(password, personalData);
        if (!failed.isEmpty()) {
            StringBuilder message = new StringBuilder("La contraseña no cumple las reglas: ");
            for (int i = 0; i < failed.size(); i++) {
                if (i > 0) message.append("; ");
                message.append(failed.get(i).description().toLowerCase(Locale.ROOT));
            }
            throw ApiException.badRequest(message.toString());
        }
    }

    private static boolean hasRepeats(String pw) {
        int run = 1;
        for (int i = 1; i < pw.length(); i++) {
            run = pw.charAt(i) == pw.charAt(i - 1) ? run + 1 : 1;
            if (run >= 3) return true;
        }
        return false;
    }

    /** 4 caracteres consecutivos ascendentes o descendentes: 1234, 4321, abcd, dcba. */
    private static boolean hasSequence(String folded) {
        int up = 1;
        int down = 1;
        for (int i = 1; i < folded.length(); i++) {
            char prev = folded.charAt(i - 1);
            char cur = folded.charAt(i);
            boolean alnum = Character.isLetterOrDigit(prev) && Character.isLetterOrDigit(cur);
            up = alnum && cur == prev + 1 ? up + 1 : 1;
            down = alnum && cur == prev - 1 ? down + 1 : 1;
            if (up >= 4 || down >= 4) return true;
        }
        return false;
    }

    /** Partes del nombre y del correo de 3 o más letras (sin tildes ni mayúsculas) que no deben aparecer. */
    private static boolean containsPersonalData(String folded, String... personalData) {
        if (personalData == null) return false;
        for (String data : personalData) {
            if (data == null || data.isBlank()) continue;
            String value = fold(data);
            int at = value.indexOf('@');
            if (at > 0) value = value.substring(0, at); // del correo solo importa lo que va antes de la @
            for (String part : value.split("[^a-z0-9]+")) {
                if (part.length() >= 3 && folded.contains(part)) return true;
            }
        }
        return false;
    }

    /** Minúsculas y sin tildes, para que "Ésteban" y "esteban" se traten igual. */
    static String fold(String text) {
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT);
    }
}
