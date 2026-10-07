package com.home.ia.domain.model.alert;

import com.home.ia.domain.model.device.DeviceId;
import com.home.ia.domain.model.home.HomeId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class Alert {
    private static final int MAX_DELIVERY_ATTEMPTS = 3;

    private final AlertId id;
    private final HomeId homeId;
    private final DeviceId deviceId; // null si la alerta es de toda la casa
    private final AlertType type;
    private final AlertSeverity severity;
    private final String message;
    private final Instant createdAt;
    private AlertStatus status;
    private int deliveryAttempts;
    private Instant sentAt;

    public Alert(AlertId id, HomeId homeId, DeviceId deviceId, AlertType type,
            AlertSeverity severity, String message, Instant createdAt,
            AlertStatus status, int deliveryAttempts, Instant sentAt) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("La alerta debe tener un mensaje");
        }
        this.id = Objects.requireNonNull(id);
        this.homeId = Objects.requireNonNull(homeId);
        this.deviceId = deviceId;
        this.type = Objects.requireNonNull(type);
        this.severity = Objects.requireNonNull(severity);
        this.message = message;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.status = Objects.requireNonNull(status);
        this.deliveryAttempts = deliveryAttempts;
        this.sentAt = sentAt;
    }

    public static Alert create(HomeId homeId, DeviceId deviceId, AlertType type,
            String message, Instant now) {
        return new Alert(AlertId.generate(), homeId, deviceId, type,
                type.getDefaultSeverity(), message, now,
                AlertStatus.PENDING, 0, null);
    }

    public void markSent(Instant at) {
        if (status == AlertStatus.SENT) {
            throw new IllegalStateException("La alerta ya fue enviada");
        }
        this.status = AlertStatus.SENT;
        this.sentAt = Objects.requireNonNull(at);
    }

    public void registerFailedAttempt() {
        this.deliveryAttempts++;
        this.status = deliveryAttempts >= MAX_DELIVERY_ATTEMPTS
                ? AlertStatus.FAILED
                : AlertStatus.PENDING;
    }

    public boolean canRetry() {
        return status == AlertStatus.PENDING;
    }

    public AlertId getId() {
        return id;
    }

    public HomeId getHomeId() {
        return homeId;
    }

    public Optional<DeviceId> getDeviceId() {
        return Optional.ofNullable(deviceId);
    }

    public AlertType getType() {
        return type;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public Optional<Instant> getSentAt() {
        return Optional.ofNullable(sentAt);
    }
}