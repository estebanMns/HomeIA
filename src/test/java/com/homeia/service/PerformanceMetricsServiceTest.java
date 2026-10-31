package com.homeia.service;

import com.homeia.service.PerformanceMetricsService.MetricSummary;
import com.homeia.service.PerformanceMetricsService.Sample;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerformanceMetricsServiceTest {

    private final PerformanceMetricsService service = new PerformanceMetricsService();
    private final UUID home = UUID.randomUUID();

    private MetricSummary summaryOf(UUID homeId, String name) {
        return service.summary(homeId).stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void calculaElP75() {
        assertEquals(0, PerformanceMetricsService.percentile(new double[0], 75));
        assertEquals(3, PerformanceMetricsService.percentile(new double[] {4, 1, 3, 2}, 75));
        assertEquals(75, PerformanceMetricsService.percentile(java.util.stream.IntStream.rangeClosed(1, 100)
                .asDoubleStream().toArray(), 75));
    }

    @Test
    void clasificaConLosUmbralesDeWebDev() {
        service.record(home, List.of(new Sample("LCP", 2400), new Sample("INP", 350), new Sample("CLS", 0.3)));
        assertEquals("good", summaryOf(home, "LCP").rating());
        assertEquals("needs-improvement", summaryOf(home, "INP").rating());
        assertEquals("poor", summaryOf(home, "CLS").rating());
        assertNull(summaryOf(home, "TTFB").rating(), "sin muestras no hay clasificación");
        assertEquals(0, summaryOf(home, "TTFB").count());
    }

    @Test
    void conservaSoloLasUltimasMuestras() {
        for (int i = 0; i < PerformanceMetricsService.MAX_SAMPLES + 20; i += 10) {
            List<Sample> batch = new ArrayList<>();
            for (int j = 0; j < 10; j++) batch.add(new Sample("TTFB", i + j));
            service.record(home, batch);
        }
        assertEquals(PerformanceMetricsService.MAX_SAMPLES, summaryOf(home, "TTFB").count());
    }

    @Test
    void separaLasMetricasPorCasa() {
        service.record(home, List.of(new Sample("FCP", 1000)));
        assertEquals(0, summaryOf(UUID.randomUUID(), "FCP").count());
    }

    @Test
    void rechazaNombresValoresYLotesInvalidos() {
        assertThrows(ApiException.class, () -> service.record(home, List.of(new Sample("CPU", 1))));
        assertThrows(ApiException.class, () -> service.record(home, List.of(new Sample(null, 1))));
        assertThrows(ApiException.class, () -> service.record(home, List.of(new Sample("LCP", -1))));
        assertThrows(ApiException.class, () -> service.record(home, List.of(new Sample("LCP", Double.NaN))));
        List<Sample> tooMany = new ArrayList<>();
        for (int i = 0; i <= PerformanceMetricsService.MAX_BATCH; i++) tooMany.add(new Sample("LCP", 1));
        assertThrows(ApiException.class, () -> service.record(home, tooMany));
        assertEquals(0, summaryOf(home, "LCP").count(), "un lote inválido no guarda nada");
    }
}
