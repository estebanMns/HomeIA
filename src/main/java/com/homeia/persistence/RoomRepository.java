package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {

    List<RoomEntity> findByHomeIdOrderByNameAsc(UUID homeId);

    Optional<RoomEntity> findByIdAndHomeId(UUID id, UUID homeId);

    boolean existsByHomeIdAndNameIgnoreCase(UUID homeId, String name);
}
