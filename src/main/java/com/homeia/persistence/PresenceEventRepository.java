package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PresenceEventRepository extends JpaRepository<PresenceEventEntity, Long> {

    List<PresenceEventEntity> findByRoomIdAndObservedAtBetween(UUID roomId, Instant from, Instant to);

    Optional<PresenceEventEntity> findFirstByRoomIdOrderByObservedAtDesc(UUID roomId);
}
