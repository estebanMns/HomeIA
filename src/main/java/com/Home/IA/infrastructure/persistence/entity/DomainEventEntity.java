package com.home.ia.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "domain_events", indexes = {
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_occurred_at", columnList = "occurred_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainEventEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String eventType;

    @Column
    private String roomId;

    @Column
    private String deviceId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant createdAt;
}
