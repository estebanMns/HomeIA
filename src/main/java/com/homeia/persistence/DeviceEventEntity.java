package com.homeia.persistence;

import com.homeia.domain.DeviceEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Entrada del historial de un dispositivo (o de la casa, si deviceId es null). */
@Entity
@Table(name = "device_events")
@Getter
@Setter
@NoArgsConstructor
public class DeviceEventEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "edge_id")
    private UUID edgeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DeviceEventType type;

    @Column(length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DeviceEventEntity(UUID homeId, UUID deviceId, UUID edgeId, DeviceEventType type, String details) {
        this.homeId = homeId;
        this.deviceId = deviceId;
        this.edgeId = edgeId;
        this.type = type;
        this.details = details == null ? null : (details.length() > 1000 ? details.substring(0, 1000) : details);
    }
}
