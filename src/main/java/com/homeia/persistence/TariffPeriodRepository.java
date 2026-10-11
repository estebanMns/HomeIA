package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TariffPeriodRepository extends JpaRepository<TariffPeriodEntity, UUID> {

    List<TariffPeriodEntity> findByHomeIdOrderByPeriodStartAsc(UUID homeId);

    Optional<TariffPeriodEntity> findByIdAndHomeId(UUID id, UUID homeId);

    boolean existsByHomeIdAndPeriodStart(UUID homeId, LocalDate periodStart);

    /** Tarifa vigente en una fecha: el período que contiene el día (o el último que empezó antes). */
    Optional<TariffPeriodEntity> findFirstByHomeIdAndPeriodStartLessThanEqualOrderByPeriodStartDesc(UUID homeId, LocalDate date);
}
