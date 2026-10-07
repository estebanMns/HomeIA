package com.home.ia.domain.model.alert;

public enum AlertType {
    EXCESSIVE_CONSUMPTION(AlertSeverity.WARNING),
    POSSIBLE_ENERGY_LEAK(AlertSeverity.CRITICAL),
    DEVICE_OFFLINE(AlertSeverity.INFO);

    private final AlertSeverity defaultSeverity;

    AlertType(AlertSeverity defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }

    public AlertSeverity getDefaultSeverity() {
        return defaultSeverity;
    }
}