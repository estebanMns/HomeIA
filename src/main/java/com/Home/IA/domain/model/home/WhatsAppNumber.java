package com.home.ia.domain.model.home;

import java.util.regex.Pattern;

public record WhatsAppNumber(String value) {
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    public WhatsAppNumber {
        if (value == null || !E164.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Número inválido. Usa formato internacional, ej: +573001234567");
        }
    }
}