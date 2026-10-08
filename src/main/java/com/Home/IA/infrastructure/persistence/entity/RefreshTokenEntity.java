package com.home.ia.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean revoked;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean used;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant usedAt;

    @Column
    private Instant revokedAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !used && !isExpired();
    }
}
