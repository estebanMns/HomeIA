package com.homeia.persistence;

import com.homeia.domain.ActionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_actions")
@Getter
@Setter
@NoArgsConstructor
public class AutomationActionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private ActionType actionType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "watts_avoided", nullable = false)
    private double wattsAvoided;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
