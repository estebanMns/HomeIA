package com.homeia.persistence;

import com.homeia.domain.EdgeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** HomeIA Edge enrolado en una casa. Los secretos se guardan cifrados (SecretCipher). */
@Entity
@Table(name = "edge_nodes")
@Getter
@Setter
@NoArgsConstructor
public class EdgeNodeEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "secret_ciphertext", nullable = false, columnDefinition = "text")
    private String secretCiphertext;

    @Column(name = "previous_secret_ciphertext", columnDefinition = "text")
    private String previousSecretCiphertext;

    @Column(name = "previous_valid_until")
    private Instant previousValidUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EdgeStatus status = EdgeStatus.PENDING;

    @Column(length = 40)
    private String version;

    @Column(length = 120)
    private String platform;

    /** online / offline / error según zigbee2mqtt/bridge/state; null si el Edge no tiene Zigbee. */
    @Column(name = "zigbee_bridge_state", length = 16)
    private String zigbeeBridgeState;

    @Column(name = "zigbee_permit_join", nullable = false)
    private boolean zigbeePermitJoin;

    /** Hasta cuándo HomeIA autorizó el emparejamiento. */
    @Column(name = "zigbee_permit_join_until")
    private Instant zigbeePermitJoinUntil;

    @Column(name = "queue_depth", nullable = false)
    private int queueDepth;

    @Column(name = "metrics_json", nullable = false, columnDefinition = "text")
    private String metricsJson = "{}";

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "last_hello_at")
    private Instant lastHelloAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
