package com.home.ia.infrastructure.persistence.repository;

import com.home.ia.infrastructure.persistence.entity.EnergyConsumptionHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EnergyConsumptionHistoryJpaRepository extends JpaRepository<EnergyConsumptionHistoryEntity, String> {
    List<EnergyConsumptionHistoryEntity> findByDeviceId(String deviceId);
    List<EnergyConsumptionHistoryEntity> findByDeviceIdAndMeasuredAtAfter(String deviceId, Instant measuredAt);
}
