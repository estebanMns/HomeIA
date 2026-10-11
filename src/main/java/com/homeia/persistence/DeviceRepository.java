package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {

    List<DeviceEntity> findByHomeIdOrderByNameAsc(UUID homeId);

    List<DeviceEntity> findByHomeIdAndRoomIdOrderByNameAsc(UUID homeId, UUID roomId);

    Optional<DeviceEntity> findByIdAndHomeId(UUID id, UUID homeId);

    List<DeviceEntity> findByRoomId(UUID roomId);

    Optional<DeviceEntity> findByHomeIdAndBluetoothId(UUID homeId, String bluetoothId);

    long countByHomeId(UUID homeId);
}
