package com.homeia.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeakDetectionPolicyTest {

    private static final int FUGA_MIN = 60;

    private static List<Double> historial(double watts, int n) {
        return new ArrayList<>(Collections.nCopies(n, watts));
    }

    @Test
    void laNeveraConSuConsumoHabitualNoEsUnaFuga() {
        // El bug anterior: un crítico encendido en habitación vacía disparaba alerta crítica cada noche
        Optional<LeakDetectionPolicy.Suspicion> r =
                LeakDetectionPolicy.evaluate(150, historial(150, 20), true, false, 300, FUGA_MIN);
        assertTrue(r.isEmpty());
    }

    @Test
    void consumoMuyPorEncimaDeLaLineaBaseConLaHabitacionVaciaEsSospechoso() {
        Optional<LeakDetectionPolicy.Suspicion> r =
                LeakDetectionPolicy.evaluate(1500, historial(40, 20), true, false, 90, FUGA_MIN);
        assertTrue(r.isPresent());
        assertEquals(40, r.get().baselineWatts(), 1e-9);
        assertEquals(100, r.get().anomalyPercent());
    }

    @Test
    void noSospechaSiLaHabitacionEstaOcupadaOVaciaHacePoco() {
        assertTrue(LeakDetectionPolicy.evaluate(1500, historial(40, 20), true, true, 0, FUGA_MIN).isEmpty());
        assertTrue(LeakDetectionPolicy.evaluate(1500, historial(40, 20), true, false, 30, FUGA_MIN).isEmpty());
        assertTrue(LeakDetectionPolicy.evaluate(1500, historial(40, 20), false, false, 300, FUGA_MIN).isEmpty());
    }

    @Test
    void sinHistorialSuficienteNoAlerta() {
        assertTrue(LeakDetectionPolicy.evaluate(1500, historial(40, 5), true, false, 300, FUGA_MIN).isEmpty());
        assertTrue(LeakDetectionPolicy.evaluate(1500, null, true, false, 300, FUGA_MIN).isEmpty());
    }

    @Test
    void variacionesPequenasNoSonFuga() {
        // 10 W de línea base: el umbral es max(15, 60) = 60 W
        assertTrue(LeakDetectionPolicy.evaluate(55, historial(10, 20), true, false, 300, FUGA_MIN).isEmpty());
        assertFalse(LeakDetectionPolicy.evaluate(60, historial(10, 20), true, false, 300, FUGA_MIN).isEmpty());
    }

    @Test
    void laMedianaIgnoraPicosAislados() {
        List<Double> conPicos = historial(40, 18);
        conPicos.add(2000.0);
        conPicos.add(2000.0);
        assertEquals(40, LeakDetectionPolicy.median(conPicos), 1e-9);
    }
}
