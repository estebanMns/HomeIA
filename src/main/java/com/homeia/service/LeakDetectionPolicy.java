package com.homeia.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Detección de posibles fugas de energía por CONSUMO ANÓMALO.
 *
 * Antes se alertaba cuando un dispositivo crítico seguía encendido en una habitación vacía, pero eso es lo
 * normal (la nevera y el router siempre lo están) y generaba alertas críticas falsas todas las noches.
 *
 * Ahora hay sospecha de fuga solo si, con la habitación vacía el tiempo configurado, el dispositivo consume
 * claramente más que su línea base: la mediana de lo que consume a esta misma hora en los días anteriores.
 * Sin historial suficiente no se alerta (ante la duda, no se molesta al usuario).
 */
final class LeakDetectionPolicy {

    /** Lecturas mínimas a esta misma hora para confiar en la línea base. */
    static final int MIN_SAMPLES = 12;
    /** El consumo debe superar la línea base en al menos este factor... */
    static final double ANOMALY_FACTOR = 1.5;
    /** ...y en al menos estos vatios, para no alertar por variaciones pequeñas. */
    static final double MIN_EXCESS_WATTS = 50;

    record Suspicion(double baselineWatts, double currentWatts, double anomalyScore) {
        int anomalyPercent() {
            return (int) Math.round(anomalyScore * 100);
        }
    }

    private LeakDetectionPolicy() {
    }

    static Optional<Suspicion> evaluate(double currentWatts, List<Double> sameHourSamples, boolean presenceKnown,
                                        boolean roomOccupied, long vacantMinutes, int leakMinutes) {
        if (!presenceKnown || roomOccupied || vacantMinutes < leakMinutes) {
            return Optional.empty();
        }
        if (sameHourSamples == null || sameHourSamples.size() < MIN_SAMPLES) {
            return Optional.empty();
        }
        double baseline = median(sameHourSamples);
        double threshold = Math.max(baseline * ANOMALY_FACTOR, baseline + MIN_EXCESS_WATTS);
        if (currentWatts < threshold) {
            return Optional.empty();
        }
        // Puntaje de anomalía: 0,5 justo en el umbral y 1 cuando lo supera otra vez en (umbral - línea base)
        double margin = threshold - baseline;
        double score = Math.min(1.0, 0.5 + (currentWatts - threshold) / (2 * margin));
        return Optional.of(new Suspicion(baseline, currentWatts, score));
    }

    static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
