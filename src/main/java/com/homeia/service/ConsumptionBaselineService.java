package com.homeia.service;

import com.homeia.persistence.EnergyReadingEntity;
import com.homeia.persistence.EnergyReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Línea base de consumo de cada dispositivo: lo que suele consumir a esta misma hora del día
 * en las dos semanas anteriores. La usa {@link LeakDetectionPolicy} para reconocer consumo anómalo.
 *
 * Se excluyen las últimas horas para que una fuga en curso no "contamine" su propia línea base,
 * y el resultado se guarda una hora para no leer 14 días de lecturas en cada ciclo.
 */
@Service
@RequiredArgsConstructor
public class ConsumptionBaselineService {

    private static final Duration HISTORY = Duration.ofDays(14);
    private static final Duration EXCLUDE_RECENT = Duration.ofHours(3);
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private record Key(UUID deviceId, int hour) {
    }

    private record Entry(Instant computedAt, List<Double> samples) {
    }

    private final EnergyReadingRepository energyRepository;
    private final Map<Key, Entry> cache = new ConcurrentHashMap<>();

    /** Lecturas (en vatios) del dispositivo a la misma hora local de {@code now}, en los días anteriores. */
    @Transactional(readOnly = true)
    public List<Double> sameHourSamples(UUID deviceId, ZoneId zone, Instant now) {
        int hour = ZonedDateTime.ofInstant(now, zone).getHour();
        Key key = new Key(deviceId, hour);
        Entry cached = cache.get(key);
        if (cached != null && cached.computedAt().plus(CACHE_TTL).isAfter(now)) {
            return cached.samples();
        }
        List<Double> samples = energyRepository
                .findByDeviceIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(deviceId, now.minus(HISTORY), now.minus(EXCLUDE_RECENT))
                .stream()
                .filter(r -> ZonedDateTime.ofInstant(r.getMeasuredAt(), zone).getHour() == hour)
                .map(EnergyReadingEntity::getWatts)
                .toList();
        cache.put(key, new Entry(now, samples));
        return samples;
    }
}
