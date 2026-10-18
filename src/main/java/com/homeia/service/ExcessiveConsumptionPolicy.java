package com.homeia.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Consumo excesivo SOSTENIDO.
 *
 * Antes bastaba una lectura instantánea por encima de un umbral global y además se comparaba con ">",
 * así que un calentador de 1.500 W nunca superaba el umbral por defecto de 1.500 W, y un microondas
 * encendido dos minutos sí disparaba una alerta.
 *
 * Ahora el dispositivo debe estar en o por encima de su umbral durante todo el tiempo configurado.
 * Los dispositivos suelen reportar solo cuando cambian, así que se toma la última lectura anterior al
 * inicio de la ventana como el valor vigente en ese momento.
 */
final class ExcessiveConsumptionPolicy {

    record Sample(Instant at, double watts) {
    }

    private ExcessiveConsumptionPolicy() {
    }

    /** Umbral efectivo: el del dispositivo si lo tiene, si no el global de la casa. */
    static double thresholdFor(Double deviceThreshold, double homeThreshold) {
        return deviceThreshold != null && deviceThreshold > 0 ? deviceThreshold : homeThreshold;
    }

    static boolean isSustained(List<Sample> samples, Instant now, double thresholdWatts, Duration sustainedFor) {
        if (samples == null || samples.isEmpty() || thresholdWatts <= 0) {
            return false;
        }
        Instant windowStart = now.minus(sustainedFor);
        List<Sample> sorted = samples.stream()
                .filter(s -> !s.at().isAfter(now))
                .sorted(Comparator.comparing(Sample::at))
                .toList();

        Sample anchor = null;
        for (Sample s : sorted) {
            if (!s.at().isAfter(windowStart)) {
                anchor = s; // última lectura en o antes del inicio de la ventana
            }
        }
        if (anchor == null) {
            return false; // no hay datos que cubran toda la ventana
        }
        for (Sample s : sorted) {
            if (!s.at().isBefore(anchor.at()) && s.watts() < thresholdWatts) {
                return false;
            }
        }
        return true;
    }

    static double averageWatts(List<Sample> samples) {
        return samples.stream().mapToDouble(Sample::watts).average().orElse(0);
    }
}
