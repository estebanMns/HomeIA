package com.home.ia.infrastructure.persistence.repository;

import com.home.ia.infrastructure.persistence.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceJpaRepository extends JpaRepository<DeviceEntity, String> {
    List<DeviceEntity> findByRoomId(String roomId);
    List<DeviceEntity> findByRoomIdAndTypeIn(String roomId, java.util.List<String> types);
}
