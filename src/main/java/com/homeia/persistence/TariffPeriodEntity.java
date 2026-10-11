package com.homeia.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tarifa de un período de facturación, tomada del recibo: costo unitario (CUv), subsidio por tramo
 * (primeros kWh con un porcentaje), alumbrado público y ajuste monetario.
 */
@Entity
@Table(name = "tariff_periods")
@Getter
@Setter
@NoArgsConstructor
public class TariffPeriodEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "unit_price_per_kwh", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPricePerKwh;

    @Column(name = "subsidy_kwh_limit", nullable = false, precision = 10, scale = 2)
    private BigDecimal subsidyKwhLimit;

    @Column(name = "subsidy_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal subsidyPercent;

    @Column(name = "public_lighting", nullable = false, precision = 12, scale = 2)
    private BigDecimal publicLighting;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal adjustment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
