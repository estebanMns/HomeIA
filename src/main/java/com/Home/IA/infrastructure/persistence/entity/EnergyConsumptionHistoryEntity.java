package com.home.ia.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "energy_consumption_history", indexes = {
    @Index(name = "idx_device_id", columnList = "device_id"),
    @Index(name = "idx_measured_at", columnList = "measured_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnergyConsumptionHistoryEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private Double consumptionWatts;

    @Column(nullable = false)
    private Instant measuredAt;

    @Column(nullable = false)
    private Instant createdAt;
}
