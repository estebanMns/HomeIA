package com.homeia.service;

import com.homeia.persistence.MeterReadingEntity;
import com.homeia.persistence.MeterReadingRepository;
import com.homeia.persistence.TariffPeriodEntity;
import com.homeia.persistence.TariffPeriodRepository;
import com.homeia.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Facturación según el recibo de energía.
 *
 * Consumo del período = lectura del medidor al final − lectura al inicio.
 * Energía bruta      = kWh × costo unitario (CUv).
 * Subsidio           = min(kWh, límite subsidiado) × CUv × porcentaje.
 * Energía neta       = bruta − subsidio (sin redondear); se redondea a pesos al final.
 * Total              = energía neta + ajuste monetario + alumbrado público.
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final TariffPeriodRepository tariffRepository;
    private final MeterReadingRepository readingRepository;
    private final AuditService auditService;

    public record Bill(UUID tariffId, LocalDate periodStart, LocalDate periodEnd, BigDecimal consumptionKwh,
                       BigDecimal energyGross, BigDecimal subsidy, BigDecimal energyNet, BigDecimal adjustment,
                       BigDecimal publicLighting, BigDecimal total) {
    }

    public record BillStatus(TariffPeriodEntity tariff, Bill bill, String missing) {
    }

    // ---------- Cálculo ----------

    /** Calcula la factura de un período con las lecturas de inicio y fin. */
    static Bill compute(TariffPeriodEntity tariff, BigDecimal startKwh, BigDecimal endKwh) {
        BigDecimal consumption = endKwh.subtract(startKwh);
        if (consumption.signum() < 0) {
            throw ApiException.badRequest("La lectura final es menor que la inicial: revisa las lecturas del medidor");
        }
        BigDecimal unit = tariff.getUnitPricePerKwh();
        BigDecimal gross = consumption.multiply(unit);
        BigDecimal subsidized = consumption.min(tariff.getSubsidyKwhLimit())
                .multiply(unit)
                .multiply(tariff.getSubsidyPercent())
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(subsidized);

        BigDecimal netRounded = round(net);
        BigDecimal adjustment = round(tariff.getAdjustment());
        BigDecimal lighting = round(tariff.getPublicLighting());
        return new Bill(tariff.getId(), tariff.getPeriodStart(), tariff.getPeriodEnd(), consumption.setScale(3, RoundingMode.HALF_UP),
                round(gross), round(subsidized), netRounded, adjustment, lighting,
                netRounded.add(adjustment).add(lighting));
    }

    /**
     * Costo de energía para un consumo, con la tarifa vigente en la fecha dada (subsidio por tramo aplicado
     * a ese consumo). Si no hay tarifa cargada, usa la tarifa simple de la casa.
     */
    @Transactional(readOnly = true)
    public BigDecimal energyCost(UUID homeId, BigDecimal kwh, Instant asOf, BigDecimal fallbackPrice, ZoneId zone) {
        LocalDate day = asOf.atZone(zone).toLocalDate();
        Optional<TariffPeriodEntity> tariff = tariffRepository
                .findFirstByHomeIdAndPeriodStartLessThanEqualOrderByPeriodStartDesc(homeId, day)
                .filter(t -> !day.isAfter(t.getPeriodEnd()));
        if (tariff.isEmpty()) {
            return kwh.multiply(fallbackPrice);
        }
        TariffPeriodEntity t = tariff.get();
        BigDecimal subsidized = kwh.min(t.getSubsidyKwhLimit())
                .multiply(t.getUnitPricePerKwh())
                .multiply(t.getSubsidyPercent())
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);
        return kwh.multiply(t.getUnitPricePerKwh()).subtract(subsidized);
    }

    /** Recibos de todas las tarifas cargadas. Si faltan lecturas del medidor, indica cuál. */
    @Transactional(readOnly = true)
    public List<BillStatus> bills(UUID homeId) {
        List<MeterReadingEntity> readings = readingRepository.findByHomeIdOrderByReadAtAsc(homeId);
        return tariffRepository.findByHomeIdOrderByPeriodStartAsc(homeId).stream().map(t -> {
            Optional<MeterReadingEntity> start = readingFor(readings, t.getPeriodStart());
            Optional<MeterReadingEntity> end = readingFor(readings, t.getPeriodEnd());
            if (start.isEmpty()) {
                return new BillStatus(t, null, "Falta la lectura del medidor del " + t.getPeriodStart());
            }
            if (end.isEmpty()) {
                return new BillStatus(t, null, "Falta la lectura del medidor del " + t.getPeriodEnd());
            }
            return new BillStatus(t, compute(t, start.get().getReadingKwh(), end.get().getReadingKwh()), null);
        }).toList();
    }

    private static Optional<MeterReadingEntity> readingFor(List<MeterReadingEntity> readings, LocalDate date) {
        return readings.stream().filter(r -> r.getReadAt().equals(date)).findFirst();
    }

    // ---------- Tarifas ----------

    @Transactional(readOnly = true)
    public List<TariffPeriodEntity> tariffs(UUID homeId) {
        return tariffRepository.findByHomeIdOrderByPeriodStartAsc(homeId);
    }

    @Transactional
    public TariffPeriodEntity createTariff(AuthPrincipal principal, TariffInput in) {
        validateTariff(in);
        if (tariffRepository.existsByHomeIdAndPeriodStart(principal.homeId(), in.periodStart())) {
            throw ApiException.conflict("Ya existe una tarifa que empieza el " + in.periodStart());
        }
        TariffPeriodEntity t = new TariffPeriodEntity();
        t.setHomeId(principal.homeId());
        apply(t, in);
        tariffRepository.save(t);
        auditService.record(principal, "TARIFF_CREATED", "TARIFF", t.getId().toString(),
                in.periodStart() + " a " + in.periodEnd() + " CUv " + in.unitPricePerKwh());
        return t;
    }

    @Transactional
    public TariffPeriodEntity updateTariff(AuthPrincipal principal, UUID id, TariffInput in) {
        validateTariff(in);
        TariffPeriodEntity t = tariffRepository.findByIdAndHomeId(id, principal.homeId())
                .orElseThrow(() -> ApiException.notFound("Tarifa"));
        if (!t.getPeriodStart().equals(in.periodStart())
                && tariffRepository.existsByHomeIdAndPeriodStart(principal.homeId(), in.periodStart())) {
            throw ApiException.conflict("Ya existe una tarifa que empieza el " + in.periodStart());
        }
        apply(t, in);
        auditService.record(principal, "TARIFF_UPDATED", "TARIFF", id.toString(), in.periodStart() + " a " + in.periodEnd());
        return t;
    }

    @Transactional
    public void deleteTariff(AuthPrincipal principal, UUID id) {
        TariffPeriodEntity t = tariffRepository.findByIdAndHomeId(id, principal.homeId())
                .orElseThrow(() -> ApiException.notFound("Tarifa"));
        tariffRepository.delete(t);
        auditService.record(principal, "TARIFF_DELETED", "TARIFF", id.toString(), t.getPeriodStart() + " a " + t.getPeriodEnd());
    }

    // ---------- Lecturas del medidor ----------

    @Transactional(readOnly = true)
    public List<MeterReadingEntity> readings(UUID homeId) {
        return readingRepository.findByHomeIdOrderByReadAtAsc(homeId);
    }

    @Transactional
    public MeterReadingEntity addReading(AuthPrincipal principal, BigDecimal kwh, LocalDate readAt) {
        if (kwh == null || kwh.signum() < 0 || readAt == null) {
            throw ApiException.badRequest("La lectura y la fecha son obligatorias");
        }
        if (readingRepository.findByHomeIdAndReadAt(principal.homeId(), readAt).isPresent()) {
            throw ApiException.conflict("Ya hay una lectura del medidor el " + readAt);
        }
        MeterReadingEntity r = new MeterReadingEntity();
        r.setHomeId(principal.homeId());
        r.setReadingKwh(kwh);
        r.setReadAt(readAt);
        readingRepository.save(r);
        auditService.record(principal, "METER_READING_ADDED", "METER_READING", r.getId().toString(),
                kwh + " kWh el " + readAt);
        return r;
    }

    @Transactional
    public void deleteReading(AuthPrincipal principal, UUID id) {
        MeterReadingEntity r = readingRepository.findByIdAndHomeId(id, principal.homeId())
                .orElseThrow(() -> ApiException.notFound("Lectura"));
        readingRepository.delete(r);
        auditService.record(principal, "METER_READING_DELETED", "METER_READING", id.toString(), r.getReadAt().toString());
    }

    // ---------- Validación y utilidades ----------

    public record TariffInput(LocalDate periodStart, LocalDate periodEnd, BigDecimal unitPricePerKwh,
                              BigDecimal subsidyKwhLimit, BigDecimal subsidyPercent, BigDecimal publicLighting,
                              BigDecimal adjustment) {
    }

    private static void apply(TariffPeriodEntity t, TariffInput in) {
        t.setPeriodStart(in.periodStart());
        t.setPeriodEnd(in.periodEnd());
        t.setUnitPricePerKwh(in.unitPricePerKwh());
        t.setSubsidyKwhLimit(in.subsidyKwhLimit() == null ? BigDecimal.ZERO : in.subsidyKwhLimit());
        t.setSubsidyPercent(in.subsidyPercent() == null ? BigDecimal.ZERO : in.subsidyPercent());
        t.setPublicLighting(in.publicLighting() == null ? BigDecimal.ZERO : in.publicLighting());
        t.setAdjustment(in.adjustment() == null ? BigDecimal.ZERO : in.adjustment());
    }

    private static void validateTariff(TariffInput in) {
        if (in.periodStart() == null || in.periodEnd() == null) {
            throw ApiException.badRequest("Las fechas del período son obligatorias");
        }
        if (!in.periodEnd().isAfter(in.periodStart())) {
            throw ApiException.badRequest("La fecha final debe ser posterior a la inicial");
        }
        if (in.unitPricePerKwh() == null || in.unitPricePerKwh().signum() <= 0) {
            throw ApiException.badRequest("El costo unitario (CUv) debe ser mayor que 0");
        }
        if (in.subsidyKwhLimit() != null && in.subsidyKwhLimit().signum() < 0) {
            throw ApiException.badRequest("El límite subsidiado no puede ser negativo");
        }
        if (in.subsidyPercent() != null && (in.subsidyPercent().signum() < 0 || in.subsidyPercent().compareTo(HUNDRED) > 0)) {
            throw ApiException.badRequest("El porcentaje de subsidio debe estar entre 0 y 100");
        }
        if (in.publicLighting() != null && in.publicLighting().signum() < 0) {
            throw ApiException.badRequest("El alumbrado público no puede ser negativo");
        }
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }
}
