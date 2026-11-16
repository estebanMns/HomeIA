package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceCredentialRepository extends JpaRepository<DeviceCredentialEntity, UUID> {
}
