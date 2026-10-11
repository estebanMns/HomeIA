package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<UserEntity> findByHomeIdOrderByFullNameAsc(UUID homeId);

    Optional<UserEntity> findByIdAndHomeId(UUID id, UUID homeId);

    long countByHomeId(UUID homeId);
}
