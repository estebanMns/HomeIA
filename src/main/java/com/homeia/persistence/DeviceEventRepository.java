package com.homeia.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeviceEventRepository extends JpaRepository<DeviceEventEntity, UUID> {

    List<DeviceEventEntity> findByDeviceIdAndHomeIdOrderByCreatedAtDesc(UUID deviceId, UUID homeId, Pageable page);

    List<DeviceEventEntity> findByHomeIdOrderByCreatedAtDesc(UUID homeId, Pageable page);

    @Modifying
    @Query("delete from DeviceEventEntity e where e.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
