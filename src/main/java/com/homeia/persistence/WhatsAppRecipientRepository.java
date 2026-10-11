package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppRecipientRepository extends JpaRepository<WhatsAppRecipientEntity, UUID> {

    List<WhatsAppRecipientEntity> findByHomeIdOrderByNameAsc(UUID homeId);

    List<WhatsAppRecipientEntity> findByHomeIdAndActiveTrue(UUID homeId);

    Optional<WhatsAppRecipientEntity> findByIdAndHomeId(UUID id, UUID homeId);

    boolean existsByHomeIdAndPhoneE164(UUID homeId, String phoneE164);
}
