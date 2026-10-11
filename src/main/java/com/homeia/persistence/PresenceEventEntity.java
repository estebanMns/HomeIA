package com.homeia.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "presence_events")
@Getter
@Setter
@NoArgsConstructor
public class PresenceEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(nullable = false)
    private boolean present;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    public PresenceEventEntity(UUID homeId, UUID roomId, boolean present, Instant observedAt) {
        this.homeId = homeId;
        this.roomId = roomId;
        this.present = present;
        this.observedAt = observedAt;
    }
}
