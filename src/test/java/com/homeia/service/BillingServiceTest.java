package com.homeia.service;

import com.homeia.persistence.TariffPeriodEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifica el cálculo contra los recibos reales de Cedenar (Pasto, estrato 1). */
class BillingServiceTest {

    private static TariffPeriodEntity tariff(String start, String end, String cuv, String pct, String lighting, String adj) {
        TariffPeriodEntity t = new TariffPeriodEntity();
        t.setPeriodStart(LocalDate.parse(start));
        t.setPeriodEnd(LocalDate.parse(end));
        t.setUnitPricePerKwh(new BigDecimal(cuv));
        t.setSubsidyKwhLimit(new BigDecimal("130"));
        t.setSubsidyPercent(new BigDecimal(pct));
        t.setPublicLighting(new BigDecimal(lighting));
        t.setAdjustment(new BigDecimal(adj));
        return t;
    }

    @Test
    void augustBillMatchesReceipt() {
        // Lecturas: 34140 (28 jul) -> 34351 (28 ago). CUv 983,19; subsidio 53,38 % de los primeros 130 kWh.
        TariffPeriodEntity t = tariff("2026-07-28", "2026-08-28", "983.19", "53.38", "2573", "1");
        BillingService.Bill b = BillingService.compute(t, new BigDecimal("34140"), new BigDecimal("34351"));

        assertEquals(0, new BigDecimal("211").compareTo(b.consumptionKwh()));
        assertEquals(0, new BigDecimal("207453").compareTo(b.energyGross()));
        assertEquals(0, new BigDecimal("68227").compareTo(b.subsidy()));
        assertEquals(0, new BigDecimal("139226").compareTo(b.energyNet()));
        assertEquals(0, new BigDecimal("141800").compareTo(b.total()));
    }

    @Test
    void septemberBillMatchesReceipt() {
        // Lecturas: 34351 (28 ago) -> 34524 (28 sep). CUv 955,76; subsidio 51,96 %.
        TariffPeriodEntity t = tariff("2026-08-28", "2026-09-28", "955.76", "51.96", "2573", "0");
        BillingService.Bill b = BillingService.compute(t, new BigDecimal("34351"), new BigDecimal("34524"));

        assertEquals(0, new BigDecimal("173").compareTo(b.consumptionKwh()));
        assertEquals(0, new BigDecimal("165346").compareTo(b.energyGross()));
        assertEquals(0, new BigDecimal("64560").compareTo(b.subsidy()));
        assertEquals(0, new BigDecimal("100787").compareTo(b.energyNet()));
        assertEquals(0, new BigDecimal("103360").compareTo(b.total()));
    }

    @Test
    void consumptionBelowSubsidyLimitIsSubsidizedOnlyUpToLimit() {
        // 100 kWh: todo entra en el tramo subsidiado (límite 130).
        TariffPeriodEntity t = tariff("2026-08-28", "2026-09-28", "1000", "50", "0", "0");
        BillingService.Bill b = BillingService.compute(t, new BigDecimal("0"), new BigDecimal("100"));
        assertEquals(0, new BigDecimal("50000").compareTo(b.subsidy()));
        assertEquals(0, new BigDecimal("50000").compareTo(b.energyNet()));
    }

    @Test
    void readingDecreaseIsRejected() {
        TariffPeriodEntity t = tariff("2026-08-28", "2026-09-28", "955.76", "51.96", "0", "0");
        assertThrows(ApiException.class,
                () -> BillingService.compute(t, new BigDecimal("34524"), new BigDecimal("34351")));
    }
}
