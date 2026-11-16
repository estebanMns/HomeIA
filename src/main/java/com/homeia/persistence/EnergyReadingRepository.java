package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EnergyReadingRepository extends JpaRepository<EnergyReadingEntity, Long> {

    List<EnergyReadingEntity> findByHomeIdAndMeasuredAtBetweenOrderByDeviceIdAscMeasuredAtAsc(
            UUID homeId, Instant from, Instant to);

    List<EnergyReadingEntity> findByDeviceIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
            UUID deviceId, Instant from, Instant to);

    @Modifying
    @Query("delete from EnergyReadingEntity r where r.measuredAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
