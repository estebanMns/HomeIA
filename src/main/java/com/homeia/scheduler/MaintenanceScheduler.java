package com.homeia.scheduler;

import com.homeia.config.HomeIaProperties;
import com.homeia.persistence.DeviceCommandRepository;
import com.homeia.persistence.DeviceEventRepository;
import com.homeia.persistence.EnergyReadingRepository;
import com.homeia.service.AuthService;
import com.homeia.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Tareas de mantenimiento: limpieza de sesiones vencidas y retención de datos de alto volumen. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceScheduler {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EnergyReadingRepository energyReadings;
    private final DeviceEventRepository deviceEvents;
    private final DeviceCommandRepository deviceCommands;
    private final HomeIaProperties properties;

    /** Todos los días a las 03:15. */
    @Scheduled(cron = "0 15 3 * * *")
    public void purgeExpiredSessions() {
        int sessions = authService.purgeExpiredTokens();
        int resets = passwordResetService.purgeExpiredTokens();
        log.info("Limpieza: {} sesiones y {} enlaces de contraseña vencidos eliminados", sessions, resets);
    }

    /** Todos los días a las 03:40: borra lecturas, eventos y órdenes más antiguos que la retención configurada. */
    @Scheduled(cron = "0 40 3 * * *")
    @Transactional
    public void applyRetention() {
        HomeIaProperties.Retention retention = properties.retention();
        if (retention == null) return;
        Instant now = Instant.now();
        int energy = retention.energyReadingDays() > 0
                ? energyReadings.deleteOlderThan(now.minus(Duration.ofDays(retention.energyReadingDays()))) : 0;
        int events = retention.deviceEventDays() > 0
                ? deviceEvents.deleteOlderThan(now.minus(Duration.ofDays(retention.deviceEventDays()))) : 0;
        int commands = retention.deviceCommandDays() > 0
                ? deviceCommands.deleteOlderThan(now.minus(Duration.ofDays(retention.deviceCommandDays()))) : 0;
        log.info("Retención: {} lecturas de energía, {} eventos y {} órdenes eliminados", energy, events, commands);
    }
}
