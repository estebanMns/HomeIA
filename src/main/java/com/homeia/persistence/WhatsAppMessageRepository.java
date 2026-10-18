package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import com.homeia.domain.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessageEntity, UUID> {

    Page<WhatsAppMessageEntity> findByHomeIdOrderByCreatedAtDesc(UUID homeId, Pageable pageable);

    Optional<WhatsAppMessageEntity> findByIdAndHomeId(UUID id, UUID homeId);

    List<WhatsAppMessageEntity> findTop50ByHomeIdAndStatusOrderByCreatedAtAsc(UUID homeId, MessageStatus status);

    long countByHomeIdAndStatus(UUID homeId, MessageStatus status);

    /** Cola de envío: mensajes pendientes cuyo siguiente intento ya venció. */
    List<WhatsAppMessageEntity> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            MessageStatus status, Instant now);
}
