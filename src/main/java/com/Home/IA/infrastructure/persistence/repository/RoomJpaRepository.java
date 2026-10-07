package com.home.ia.infrastructure.persistence.repository;

import com.home.ia.infrastructure.persistence.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomJpaRepository extends JpaRepository<RoomEntity, String> {
    List<RoomEntity> findByHomeId(String homeId);
}
