package com.home.ia.infrastructure.persistence.repository;

import com.home.ia.infrastructure.persistence.entity.DomainEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DomainEventJpaRepository extends JpaRepository<DomainEventEntity, String> {
    List<DomainEventEntity> findByEventType(String eventType);
    List<DomainEventEntity> findByRoomId(String roomId);
    List<DomainEventEntity> findByDeviceId(String deviceId);
    List<DomainEventEntity> findByOccurredAtAfter(Instant occurredAt);
}
