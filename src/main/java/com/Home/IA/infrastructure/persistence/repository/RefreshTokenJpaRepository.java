package com.home.ia.infrastructure.persistence.repository;

import com.home.ia.infrastructure.persistence.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, String> {

    Optional<RefreshTokenEntity> findByToken(String token);

    List<RefreshTokenEntity> findByUserId(String userId);

    List<RefreshTokenEntity> findByUserIdAndRevokedFalse(String userId);

    @Query("SELECT rt FROM RefreshTokenEntity rt WHERE rt.userId = :userId AND rt.revoked = false AND rt.used = false AND rt.expiresAt > CURRENT_TIMESTAMP")
    List<RefreshTokenEntity> findValidTokensByUserId(@Param("userId") String userId);

    @Query("DELETE FROM RefreshTokenEntity rt WHERE rt.expiresAt < CURRENT_TIMESTAMP")
    void deleteExpiredTokens();

    @Query("SELECT COUNT(rt) FROM RefreshTokenEntity rt WHERE rt.userId = :userId AND rt.revoked = false AND rt.used = false")
    long countValidTokensByUserId(@Param("userId") String userId);
}
