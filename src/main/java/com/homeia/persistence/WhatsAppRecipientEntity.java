package com.homeia.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_recipients")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppRecipientEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "phone_e164", nullable = false, length = 20)
    private String phoneE164;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
