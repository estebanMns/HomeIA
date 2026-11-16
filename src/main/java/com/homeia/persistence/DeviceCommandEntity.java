package com.homeia.persistence;

import com.homeia.domain.CommandStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Orden enviada a un dispositivo por HomeIA Edge. El id es el requestId que viaja al Edge. */
@Entity
@Table(name = "device_commands")
@Getter
@Setter
@NoArgsConstructor
public class DeviceCommandEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "edge_id")
    private UUID edgeId;

    @Column(nullable = false, length = 16)
    private String command;

    private Integer brightness;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommandStatus status = CommandStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 500)
    private String error;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
