package com.homeia.persistence;

import com.homeia.domain.CommandStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommandEntity, UUID> {

    List<DeviceCommandEntity> findByStatusIn(Collection<CommandStatus> statuses);

    List<DeviceCommandEntity> findByDeviceIdAndHomeIdOrderByCreatedAtDesc(UUID deviceId, UUID homeId, Pageable page);

    long countByHomeIdAndStatus(UUID homeId, CommandStatus status);

    @Modifying
    @Query("delete from DeviceCommandEntity c where c.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
