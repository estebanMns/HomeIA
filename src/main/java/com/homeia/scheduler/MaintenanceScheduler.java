package com.homeia.scheduler;

import com.homeia.service.AuthService;
import com.homeia.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tareas de mantenimiento: limpieza de sesiones vencidas. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceScheduler {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    /** Todos los días a las 03:15. */
    @Scheduled(cron = "0 15 3 * * *")
    public void purgeExpiredSessions() {
        int sessions = authService.purgeExpiredTokens();
        int resets = passwordResetService.purgeExpiredTokens();
        log.info("Limpieza: {} sesiones y {} enlaces de contraseña vencidos eliminados", sessions, resets);
    }
}
