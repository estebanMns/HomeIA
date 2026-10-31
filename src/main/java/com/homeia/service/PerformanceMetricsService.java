package com.homeia.service;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agrega las métricas de rendimiento que envían los navegadores (Web Vitals y latencia de la API).
 * Guarda en memoria las últimas {@value #MAX_SAMPLES} muestras por casa y métrica, y resume con el p75,
 * el percentil que usa Google para evaluar las Web Vitals. Se reinicia con el backend: son métricas de
 * diagnóstico, no datos de negocio.
 */
@Service
public class PerformanceMetricsService {

    static final int MAX_SAMPLES = 500;
    public static final int MAX_BATCH = 50;

    /** Umbrales de web.dev: hasta el primero es "good", después del segundo es "poor". API_LATENCY es propio. */
    public enum Metric {
        LCP(2500, 4000),
        INP(200, 500),
        CLS(0.1, 0.25),
        FCP(1800, 3000),
        TTFB(800, 1800),
        API_LATENCY(300, 1000);

        final double good;
        final double poor;

        Metric(double good, double poor) {
            this.good = good;
            this.poor = poor;
        }

        public String rate(double value) {
            return value <= good ? "good" : value <= poor ? "needs-improvement" : "poor";
        }
    }

    public record Sample(String name, double value) {
    }

    public record MetricSummary(String name, int count, double p75, String rating, double good, double poor) {
    }

    private final Map<UUID, Map<Metric, Deque<Double>>> samples = new ConcurrentHashMap<>();

    /** Registra un lote. Rechaza el lote completo si es muy grande o trae nombres o valores inválidos. */
    public void record(UUID homeId, List<Sample> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        if (batch.size() > MAX_BATCH) {
            throw ApiException.badRequest("Se aceptan hasta " + MAX_BATCH + " métricas por envío");
        }
        List<Map.Entry<Metric, Double>> valid = new ArrayList<>(batch.size());
        for (Sample sample : batch) {
            Metric metric = parse(sample.name());
            if (!Double.isFinite(sample.value()) || sample.value() < 0) {
                throw ApiException.badRequest("Valor inválido para " + metric);
            }
            valid.add(Map.entry(metric, sample.value()));
        }
        Map<Metric, Deque<Double>> home = samples.computeIfAbsent(homeId, id -> new ConcurrentHashMap<>());
        for (Map.Entry<Metric, Double> entry : valid) {
            Deque<Double> deque = home.computeIfAbsent(entry.getKey(), m -> new ArrayDeque<>());
            synchronized (deque) {
                deque.addLast(entry.getValue());
                while (deque.size() > MAX_SAMPLES) {
                    deque.removeFirst();
                }
            }
        }
    }

    /** Resumen por métrica, en el orden del enum; las métricas sin muestras salen con count 0. */
    public List<MetricSummary> summary(UUID homeId) {
        Map<Metric, Deque<Double>> home = samples.getOrDefault(homeId, Map.of());
        Map<Metric, MetricSummary> out = new LinkedHashMap<>();
        for (Metric metric : Metric.values()) {
            double[] values;
            Deque<Double> deque = home.get(metric);
            if (deque == null) {
                values = new double[0];
            } else {
                synchronized (deque) {
                    values = deque.stream().mapToDouble(Double::doubleValue).toArray();
                }
            }
            double p75 = percentile(values, 75);
            out.put(metric, new MetricSummary(metric.name(), values.length, p75,
                    values.length == 0 ? null : metric.rate(p75), metric.good, metric.poor));
        }
        return List.copyOf(out.values());
    }

    /** Percentil por rango más cercano. */
    static double percentile(double[] values, int p) {
        if (values.length == 0) {
            return 0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }

    private static Metric parse(String name) {
        try {
            return Metric.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("Métrica desconocida: " + name);
        }
    }
}
