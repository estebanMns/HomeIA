package com.homeia.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AutomationSettingsRepository extends JpaRepository<AutomationSettingsEntity, UUID> {

    List<AutomationSettingsEntity> findByEnabledTrue();
}
