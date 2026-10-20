package com.homeia.persistence;

import com.homeia.domain.DeviceProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {

    List<DeviceEntity> findByHomeIdOrderByNameAsc(UUID homeId);

    List<DeviceEntity> findByHomeIdAndRoomIdOrderByNameAsc(UUID homeId, UUID roomId);

    Optional<DeviceEntity> findByIdAndHomeId(UUID id, UUID homeId);

    List<DeviceEntity> findByRoomId(UUID roomId);

    Optional<DeviceEntity> findByHomeIdAndProviderAndExternalId(UUID homeId, DeviceProvider provider, String externalId);

    List<DeviceEntity> findByProvider(DeviceProvider provider);

    long countByHomeId(UUID homeId);
}
