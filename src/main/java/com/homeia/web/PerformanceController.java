package com.homeia.web;

import com.homeia.security.AuthPrincipal;
import com.homeia.service.PerformanceMetricsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Métricas de rendimiento que reportan los navegadores de la casa (ver docs/RENDIMIENTO.md). */
@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private final PerformanceMetricsService metricsService;

    public PerformanceController(PerformanceMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public record MetricsBody(List<PerformanceMetricsService.Sample> samples) {
    }

    @PostMapping("/metrics")
    public Map<String, Object> record(@AuthenticationPrincipal AuthPrincipal principal, @RequestBody MetricsBody body) {
        metricsService.record(principal.homeId(), body.samples());
        return Map.of("accepted", body.samples() == null ? 0 : body.samples().size());
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("metrics", metricsService.summary(principal.homeId()));
    }
}
