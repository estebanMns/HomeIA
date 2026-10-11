package com.homeia.persistence;

import com.homeia.domain.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID>, JpaSpecificationExecutor<AlertEntity> {

    Optional<AlertEntity> findByIdAndHomeId(UUID id, UUID homeId);

    long countByHomeIdAndAcknowledgedFalse(UUID homeId);

    boolean existsByHomeIdAndDeviceIdAndTypeAndAcknowledgedFalse(UUID homeId, UUID deviceId, AlertType type);
}
