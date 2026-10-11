package com.homeia.web;

import com.homeia.persistence.MeterReadingEntity;
import com.homeia.persistence.TariffPeriodEntity;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.BillingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    public record TariffDto(UUID id, LocalDate periodStart, LocalDate periodEnd, BigDecimal unitPricePerKwh,
                            BigDecimal subsidyKwhLimit, BigDecimal subsidyPercent, BigDecimal publicLighting,
                            BigDecimal adjustment) {
        static TariffDto from(TariffPeriodEntity t) {
            return new TariffDto(t.getId(), t.getPeriodStart(), t.getPeriodEnd(), t.getUnitPricePerKwh(),
                    t.getSubsidyKwhLimit(), t.getSubsidyPercent(), t.getPublicLighting(), t.getAdjustment());
        }
    }

    public record TariffBody(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd,
                             @NotNull BigDecimal unitPricePerKwh, BigDecimal subsidyKwhLimit,
                             BigDecimal subsidyPercent, BigDecimal publicLighting, BigDecimal adjustment) {
        BillingService.TariffInput toInput() {
            return new BillingService.TariffInput(periodStart, periodEnd, unitPricePerKwh, subsidyKwhLimit,
                    subsidyPercent, publicLighting, adjustment);
        }
    }

    public record ReadingDto(UUID id, BigDecimal readingKwh, LocalDate readAt) {
        static ReadingDto from(MeterReadingEntity r) {
            return new ReadingDto(r.getId(), r.getReadingKwh(), r.getReadAt());
        }
    }

    public record ReadingBody(@NotNull BigDecimal readingKwh, @NotNull LocalDate readAt) {
    }

    public record BillDto(TariffDto tariff, BigDecimal consumptionKwh, BigDecimal energyGross, BigDecimal subsidy,
                          BigDecimal energyNet, BigDecimal adjustment, BigDecimal publicLighting, BigDecimal total,
                          String missing) {
    }

    // ----- Tarifas -----

    @GetMapping("/tariffs")
    public List<TariffDto> tariffs(@AuthenticationPrincipal AuthPrincipal principal) {
        return billingService.tariffs(principal.homeId()).stream().map(TariffDto::from).toList();
    }

    @PostMapping("/tariffs")
    @PreAuthorize("hasRole('ADMIN')")
    public TariffDto createTariff(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody TariffBody body) {
        return TariffDto.from(billingService.createTariff(principal, body.toInput()));
    }

    @PutMapping("/tariffs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TariffDto updateTariff(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                  @Valid @RequestBody TariffBody body) {
        return TariffDto.from(billingService.updateTariff(principal, id, body.toInput()));
    }

    @DeleteMapping("/tariffs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteTariff(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        billingService.deleteTariff(principal, id);
    }

    // ----- Lecturas del medidor -----

    @GetMapping("/meter-readings")
    public List<ReadingDto> readings(@AuthenticationPrincipal AuthPrincipal principal) {
        return billingService.readings(principal.homeId()).stream().map(ReadingDto::from).toList();
    }

    @PostMapping("/meter-readings")
    @PreAuthorize("hasRole('ADMIN')")
    public ReadingDto addReading(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ReadingBody body) {
        return ReadingDto.from(billingService.addReading(principal, body.readingKwh(), body.readAt()));
    }

    @DeleteMapping("/meter-readings/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteReading(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        billingService.deleteReading(principal, id);
    }

    // ----- Recibos calculados -----

    /** Recibo de cada período con tarifa. Si faltan lecturas del medidor, devuelve el motivo en "missing". */
    @GetMapping("/bills")
    public List<BillDto> bills(@AuthenticationPrincipal AuthPrincipal principal) {
        return billingService.bills(principal.homeId()).stream().map(s -> {
            BillingService.Bill b = s.bill();
            return new BillDto(TariffDto.from(s.tariff()),
                    b == null ? null : b.consumptionKwh(), b == null ? null : b.energyGross(),
                    b == null ? null : b.subsidy(), b == null ? null : b.energyNet(),
                    b == null ? null : b.adjustment(), b == null ? null : b.publicLighting(),
                    b == null ? null : b.total(), s.missing());
        }).toList();
    }
}
