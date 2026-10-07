package com.home.ia.infrastructure.persistence.entity;

import com.home.ia.domain.model.alert.AlertSeverity;
import com.home.ia.domain.model.alert.AlertStatus;
import com.home.ia.domain.model.alert.AlertType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String homeId;

    @Column(nullable = false)
    private String roomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant resolvedAt;

    @Column
    private String whatsappSent;
}
