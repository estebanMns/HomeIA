package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AutomationActionRepository extends JpaRepository<AutomationActionEntity, UUID> {

    Page<AutomationActionEntity> findByHomeIdOrderByCreatedAtDesc(UUID homeId, Pageable pageable);

    List<AutomationActionEntity> findByHomeIdAndCreatedAtAfter(UUID homeId, Instant after);
}
