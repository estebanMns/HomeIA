package com.home.ia.infrastructure.persistence.entity;

import com.home.ia.domain.model.device.ConnectionStatus;
import com.home.ia.domain.model.device.DeviceType;
import com.home.ia.domain.model.device.PowerState;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus connectionStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PowerState powerState;

    @Column(nullable = false)
    private Instant lastStatusChange;

    @Column
    private Integer brightness;

    @Column
    private Double consumption;

    @Column
    private Boolean critical;

    @Column
    private Boolean presence;

    @Column
    private Instant lastPresenceDetected;
}
