package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeterReadingRepository extends JpaRepository<MeterReadingEntity, UUID> {

    List<MeterReadingEntity> findByHomeIdOrderByReadAtAsc(UUID homeId);

    Optional<MeterReadingEntity> findByHomeIdAndReadAt(UUID homeId, LocalDate readAt);

    Optional<MeterReadingEntity> findByIdAndHomeId(UUID id, UUID homeId);
}
