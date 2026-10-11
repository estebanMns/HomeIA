package com.homeia.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "energy_readings")
@Getter
@Setter
@NoArgsConstructor
public class EnergyReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(nullable = false)
    private double watts;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    public EnergyReadingEntity(UUID homeId, UUID deviceId, double watts, Instant measuredAt) {
        this.homeId = homeId;
        this.deviceId = deviceId;
        this.watts = watts;
        this.measuredAt = measuredAt;
    }
}
