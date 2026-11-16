package com.homeia.persistence;

import com.homeia.domain.EdgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EdgeNodeRepository extends JpaRepository<EdgeNodeEntity, UUID> {

    List<EdgeNodeEntity> findByHomeIdOrderByCreatedAtAsc(UUID homeId);

    Optional<EdgeNodeEntity> findByIdAndHomeId(UUID id, UUID homeId);

    List<EdgeNodeEntity> findByStatusIn(Collection<EdgeStatus> statuses);

    List<EdgeNodeEntity> findByStatusNot(EdgeStatus status);
}
