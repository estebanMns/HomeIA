package com.homeia.web;

import com.homeia.security.AuthPrincipal;
import com.homeia.service.EnergyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/energy")
public class EnergyController {

    private final EnergyService energyService;

    public EnergyController(EnergyService energyService) {
        this.energyService = energyService;
    }

    public record ReadingBody(@NotNull UUID deviceId, double watts, Instant measuredAt) {
    }

    public record ReadingDto(Long id, UUID deviceId, double watts, Instant measuredAt) {
    }

    public record SummaryDto(BigDecimal totalKwh, BigDecimal cost, String currency, BigDecimal pricePerKwh,
                             double peakWatts, double currentWatts, int devicesOn, int devicesTotal,
                             double avoidedWattsEstimate, BigDecimal avoidedCostPerHour, Instant from, Instant to) {
    }

    public record BucketDto(Instant start, BigDecimal kwh, BigDecimal cost, double averageWatts) {
    }

    public record RoomEnergyDto(UUID roomId, String roomName, BigDecimal kwh, BigDecimal cost) {
    }

    /** Ingesta de una lectura de consumo (medidor externo o integración). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/readings")
    public ReadingDto record(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ReadingBody body) {
        var reading = energyService.recordReading(principal.homeId(), body.deviceId(), body.watts(), body.measuredAt());
        return new ReadingDto(reading.getId(), reading.getDeviceId(), reading.getWatts(), reading.getMeasuredAt());
    }

    @GetMapping("/summary")
    public SummaryDto summary(@AuthenticationPrincipal AuthPrincipal principal,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant[] range = range(from, to);
        EnergyService.Summary s = energyService.summary(principal.homeId(), range[0], range[1]);
        return new SummaryDto(s.totalKwh(), s.cost(), s.currency(), s.pricePerKwh(), s.peakWatts(), s.currentWatts(),
                s.devicesOn(), s.devicesTotal(), s.avoidedWattsEstimate(), s.avoidedCostPerHour(), range[0], range[1]);
    }

    /** Serie temporal de consumo. bucket=hour (por defecto) o day, en la zona horaria de la casa. */
    @GetMapping("/series")
    public List<BucketDto> series(@AuthenticationPrincipal AuthPrincipal principal,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                  @RequestParam(defaultValue = "hour") String bucket) {
        Instant[] range = range(from, to);
        return energyService.series(principal.homeId(), range[0], range[1], bucket).stream()
                .map(b -> new BucketDto(b.start(), b.kwh(), b.cost(), b.averageWatts()))
                .toList();
    }

    @GetMapping("/rooms")
    public List<RoomEnergyDto> rooms(@AuthenticationPrincipal AuthPrincipal principal,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant[] range = range(from, to);
        return energyService.byRoom(principal.homeId(), range[0], range[1]).stream()
                .map(r -> new RoomEnergyDto(r.roomId(), r.roomName(), r.kwh(), r.cost()))
                .toList();
    }

    /** Por defecto, las últimas 24 horas. */
    private static Instant[] range(Instant from, Instant to) {
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(Duration.ofHours(24));
        if (!start.isBefore(end)) {
            throw com.homeia.service.ApiException.badRequest("'from' debe ser anterior a 'to'");
        }
        return new Instant[]{start, end};
    }
}
