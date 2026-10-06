package com.home.ia.domain.model.home;

import java.util.Objects;

public class Home {
    private final HomeId id;
    private String name;
    private WhatsAppNumber alertNumber;

    public Home(HomeId id, String name, WhatsAppNumber alertNumber) {
        this.id = Objects.requireNonNull(id);
        rename(name);
        changeAlertNumber(alertNumber);
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la casa es obligatorio");
        }
        this.name = name;
    }

    public void changeAlertNumber(WhatsAppNumber alertNumber) {
        this.alertNumber = Objects.requireNonNull(alertNumber,
                "La casa necesita un número para recibir alertas");
    }

    public HomeId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public WhatsAppNumber getAlertNumber() {
        return alertNumber;
    }
}