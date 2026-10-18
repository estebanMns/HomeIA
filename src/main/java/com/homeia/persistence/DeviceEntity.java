package com.homeia.persistence;

import com.homeia.domain.ConnectionStatus;
import com.homeia.domain.DeviceType;
import com.homeia.domain.PowerState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
public class DeviceEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(name = "room_id")
    private UUID roomId;

    /** Identificador Bluetooth del dispositivo (id que entrega el navegador al conectarse por GATT). */
    @Column(name = "bluetooth_id", length = 120)
    private String bluetoothId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeviceType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 20)
    private ConnectionStatus connectionStatus = ConnectionStatus.OFFLINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "power_state", nullable = false, length = 10)
    private PowerState powerState = PowerState.OFF;

    private Integer brightness;

    @Column(name = "consumption_watts", nullable = false)
    private double consumptionWatts;

    /** Umbral propio de consumo excesivo. Null = se usa el umbral global de la casa. */
    @Column(name = "consumption_alert_watts")
    private Double consumptionAlertWatts;

    @Column(nullable = false)
    private boolean critical;

    private Boolean presence;

    @Column(name = "last_presence_at")
    private Instant lastPresenceAt;

    @Column(name = "last_status_change_at")
    private Instant lastStatusChangeAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
