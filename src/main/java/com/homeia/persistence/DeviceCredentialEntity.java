package com.homeia.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Credenciales locales de un dispositivo, solo como texto cifrado (AES-256-GCM). */
@Entity
@Table(name = "device_credentials")
@Getter
@Setter
@NoArgsConstructor
public class DeviceCredentialEntity {

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(nullable = false, columnDefinition = "text")
    private String ciphertext;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DeviceCredentialEntity(UUID deviceId, String ciphertext) {
        this.deviceId = deviceId;
        this.ciphertext = ciphertext;
    }
}
