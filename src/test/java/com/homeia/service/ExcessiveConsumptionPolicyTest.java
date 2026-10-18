package com.homeia.service;

import com.homeia.service.ExcessiveConsumptionPolicy.Sample;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcessiveConsumptionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-18T20:00:00Z");
    private static final Duration DIEZ_MIN = Duration.ofMinutes(10);

    private static Sample hace(int minutos, double watts) {
        return new Sample(NOW.minus(Duration.ofMinutes(minutos)), watts);
    }

    @Test
    void unCalentadorDe1500WSiAlcanzaUnUmbralDe1500W() {
        // El bug anterior: se comparaba con ">" y 1500 nunca superaba 1500
        assertTrue(ExcessiveConsumptionPolicy.isSustained(List.of(hace(20, 1500)), NOW, 1500, DIEZ_MIN));
    }

    @Test
    void unPicoCortoNoEsConsumoExcesivo() {
        // Microondas: 1 minuto a 1800 W
        List<Sample> muestras = List.of(hace(30, 100), hace(1, 1800));
        assertFalse(ExcessiveConsumptionPolicy.isSustained(muestras, NOW, 1500, DIEZ_MIN));
    }

    @Test
    void siBajaDelUmbralDentroDeLaVentanaNoAlerta() {
        List<Sample> muestras = List.of(hace(20, 2000), hace(5, 900), hace(2, 2000));
        assertFalse(ExcessiveConsumptionPolicy.isSustained(muestras, NOW, 1500, DIEZ_MIN));
    }

    @Test
    void usaLaUltimaLecturaAnteriorALaVentanaComoValorVigente() {
        // El dispositivo solo reporta cuando cambia: una lectura hace 25 min sigue vigente
        List<Sample> muestras = List.of(hace(40, 100), hace(25, 1600), hace(3, 1700));
        assertTrue(ExcessiveConsumptionPolicy.isSustained(muestras, NOW, 1500, DIEZ_MIN));
    }

    @Test
    void sinDatosQueCubranLaVentanaNoAlerta() {
        assertFalse(ExcessiveConsumptionPolicy.isSustained(List.of(hace(5, 2000)), NOW, 1500, DIEZ_MIN));
        assertFalse(ExcessiveConsumptionPolicy.isSustained(List.of(), NOW, 1500, DIEZ_MIN));
    }

    @Test
    void elUmbralDelDispositivoTienePrioridadSobreElDeLaCasa() {
        assertEquals(180, ExcessiveConsumptionPolicy.thresholdFor(180.0, 1500), 1e-9);
        assertEquals(1500, ExcessiveConsumptionPolicy.thresholdFor(null, 1500), 1e-9);
        assertEquals(1500, ExcessiveConsumptionPolicy.thresholdFor(0.0, 1500), 1e-9);
    }
}
