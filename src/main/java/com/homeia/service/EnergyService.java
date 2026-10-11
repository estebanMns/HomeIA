package com.homeia.service;

import com.homeia.persistence.AutomationActionEntity;
import com.homeia.persistence.AutomationActionRepository;
import com.homeia.persistence.DeviceEntity;
import com.homeia.persistence.DeviceRepository;
import com.homeia.persistence.EnergyReadingEntity;
import com.homeia.persistence.EnergyReadingRepository;
import com.homeia.persistence.HomeEntity;
import com.homeia.persistence.RoomEntity;
import com.homeia.persistence.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consumo y costo. La energía se integra por trapecios entre lecturas consecutivas de cada dispositivo;
 * si pasan más de MAX_GAP sin lecturas, ese tramo no se supone (no se inventan datos).
 */
@Service
@RequiredArgsConstructor
public class EnergyService {

    private static final Duration MAX_GAP = Duration.ofMinutes(30);

    private final EnergyReadingRepository readingRepository;
    private final DeviceRepository deviceRepository;
    private final RoomRepository roomRepository;
    private final AutomationActionRepository actionRepository;
    private final HomeService homeService;
    private final BillingService billing;

    public record Summary(BigDecimal totalKwh, BigDecimal cost, String currency, BigDecimal pricePerKwh,
                          double peakWatts, double currentWatts, int devicesOn, int devicesTotal,
                          double avoidedWattsEstimate, BigDecimal avoidedCostPerHour) {
    }

    public record Bucket(Instant start, BigDecimal kwh, BigDecimal cost, double averageWatts) {
    }

    public record RoomEnergy(UUID roomId, String roomName, BigDecimal kwh, BigDecimal cost) {
    }

    @Transactional
    public EnergyReadingEntity recordReading(UUID homeId, UUID deviceId, double watts, Instant measuredAt) {
        DeviceEntity device = deviceRepository.findByIdAndHomeId(deviceId, homeId)
                .orElseThrow(() -> ApiException.notFound("Dispositivo"));
        if (watts < 0) {
            throw ApiException.badRequest("El consumo no puede ser negativo");
        }
        Instant at = measuredAt != null ? measuredAt : Instant.now();
        device.setConsumptionWatts(watts);
        device.setUpdatedAt(Instant.now());
        return readingRepository.save(new EnergyReadingEntity(homeId, deviceId, watts, at));
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID homeId, Instant from, Instant to) {
        HomeEntity home = homeService.get(homeId);
        Map<UUID, List<EnergyReadingEntity>> byDevice = readingsByDevice(homeId, from, to);

        double totalKwh = 0;
        double peak = 0;
        for (List<EnergyReadingEntity> readings : byDevice.values()) {
            for (int i = 0; i < readings.size(); i++) {
                peak = Math.max(peak, readings.get(i).getWatts());
                if (i > 0) {
                    totalKwh += segmentKwh(readings.get(i - 1), readings.get(i));
                }
            }
        }

        List<DeviceEntity> devices = deviceRepository.findByHomeIdOrderByNameAsc(homeId);
        double currentWatts = devices.stream().mapToDouble(DeviceEntity::getConsumptionWatts).sum();
        int on = (int) devices.stream().filter(d -> d.getPowerState() == com.homeia.domain.PowerState.ON).count();

        List<AutomationActionEntity> actions = actionRepository.findByHomeIdAndCreatedAtAfter(homeId, from);
        double avoided = actions.stream()
                .filter(a -> to == null || !a.getCreatedAt().isAfter(to))
                .mapToDouble(AutomationActionEntity::getWattsAvoided).sum();

        BigDecimal price = home.getElectricityPricePerKwh();
        ZoneId zone = ZoneId.of(home.getTimezone());
        BigDecimal kwh = scale(totalKwh);
        BigDecimal avoidedPerHour = scale(avoided / 1000.0);
        Instant asOf = to != null ? to : Instant.now();
        return new Summary(
                kwh,
                billing.energyCost(homeId, kwh, asOf, price, zone).setScale(0, RoundingMode.HALF_UP),
                home.getCurrency(),
                price,
                round(peak),
                round(currentWatts),
                on,
                devices.size(),
                round(avoided),
                avoidedPerHour.multiply(price).setScale(0, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    public List<Bucket> series(UUID homeId, Instant from, Instant to, String bucket) {
        HomeEntity home = homeService.get(homeId);
        ZoneId zone = ZoneId.of(home.getTimezone());
        boolean daily = "day".equalsIgnoreCase(bucket);
        Map<Instant, double[]> totals = new java.util.TreeMap<>();

        for (List<EnergyReadingEntity> readings : readingsByDevice(homeId, from, to).values()) {
            for (int i = 1; i < readings.size(); i++) {
                EnergyReadingEntity prev = readings.get(i - 1);
                EnergyReadingEntity cur = readings.get(i);
                double kwh = segmentKwh(prev, cur);
                Instant key = bucketStart(cur.getMeasuredAt(), zone, daily);
                double[] acc = totals.computeIfAbsent(key, k -> new double[3]);
                acc[0] += kwh;
                acc[1] += cur.getWatts();
                acc[2] += 1;
            }
        }

        BigDecimal price = home.getElectricityPricePerKwh();
        List<Bucket> result = new ArrayList<>();
        totals.forEach((start, acc) -> result.add(new Bucket(
                start,
                scale(acc[0]),
                billing.energyCost(homeId, scale(acc[0]), start, price, zone).setScale(0, RoundingMode.HALF_UP),
                acc[2] > 0 ? round(acc[1] / acc[2]) : 0)));
        return result;
    }

    @Transactional(readOnly = true)
    public List<RoomEnergy> byRoom(UUID homeId, Instant from, Instant to) {
        HomeEntity home = homeService.get(homeId);
        Map<UUID, DeviceEntity> devices = deviceRepository.findByHomeIdOrderByNameAsc(homeId).stream()
                .collect(Collectors.toMap(DeviceEntity::getId, Function.identity()));
        Map<UUID, Double> kwhByRoom = new HashMap<>();
        for (Map.Entry<UUID, List<EnergyReadingEntity>> entry : readingsByDevice(homeId, from, to).entrySet()) {
            DeviceEntity device = devices.get(entry.getKey());
            if (device == null || device.getRoomId() == null) {
                continue;
            }
            List<EnergyReadingEntity> readings = entry.getValue();
            double kwh = 0;
            for (int i = 1; i < readings.size(); i++) {
                kwh += segmentKwh(readings.get(i - 1), readings.get(i));
            }
            kwhByRoom.merge(device.getRoomId(), kwh, Double::sum);
        }
        Map<UUID, RoomEntity> rooms = roomRepository.findByHomeIdOrderByNameAsc(homeId).stream()
                .collect(Collectors.toMap(RoomEntity::getId, Function.identity()));
        BigDecimal price = home.getElectricityPricePerKwh();
        return kwhByRoom.entrySet().stream()
                .map(e -> new RoomEnergy(
                        e.getKey(),
                        rooms.containsKey(e.getKey()) ? rooms.get(e.getKey()).getName() : "",
                        scale(e.getValue()),
                        billing.energyCost(homeId, scale(e.getValue()), to != null ? to : Instant.now(), price,
                                ZoneId.of(home.getTimezone())).setScale(0, RoundingMode.HALF_UP)))
                .sorted((a, b) -> b.kwh().compareTo(a.kwh()))
                .toList();
    }

    private Map<UUID, List<EnergyReadingEntity>> readingsByDevice(UUID homeId, Instant from, Instant to) {
        Instant end = to != null ? to : Instant.now();
        return readingRepository.findByHomeIdAndMeasuredAtBetweenOrderByDeviceIdAscMeasuredAtAsc(homeId, from, end)
                .stream()
                .collect(Collectors.groupingBy(EnergyReadingEntity::getDeviceId, java.util.LinkedHashMap::new,
                        Collectors.toList()));
    }

    static double segmentKwh(EnergyReadingEntity prev, EnergyReadingEntity cur) {
        long seconds = ChronoUnit.SECONDS.between(prev.getMeasuredAt(), cur.getMeasuredAt());
        if (seconds <= 0 || seconds > MAX_GAP.toSeconds()) {
            return 0;
        }
        double avgWatts = (prev.getWatts() + cur.getWatts()) / 2.0;
        return avgWatts * (seconds / 3600.0) / 1000.0;
    }

    private static Instant bucketStart(Instant at, ZoneId zone, boolean daily) {
        if (!daily) {
            return at.truncatedTo(ChronoUnit.HOURS);
        }
        LocalDate day = at.atZone(zone).toLocalDate();
        return day.atStartOfDay(zone).toInstant();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }
}
