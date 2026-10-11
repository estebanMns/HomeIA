package com.homeia.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Lectura acumulada del medidor, tal como aparece en el recibo (kWh). */
@Entity
@Table(name = "meter_readings")
@Getter
@Setter
@NoArgsConstructor
public class MeterReadingEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(name = "reading_kwh", nullable = false, precision = 14, scale = 3)
    private BigDecimal readingKwh;

    @Column(name = "read_at", nullable = false)
    private LocalDate readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
