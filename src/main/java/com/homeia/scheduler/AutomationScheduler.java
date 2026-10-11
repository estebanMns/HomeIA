package com.homeia.scheduler;

import com.homeia.service.AutomationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Ejecuta el motor de automatización en las casas que lo tienen activo. Cada casa es independiente. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationScheduler {

    private final AutomationService automationService;

    @Scheduled(fixedDelayString = "${homeia.automation.cycle-seconds:30}000",
            initialDelayString = "${homeia.automation.cycle-seconds:30}000")
    public void runCycle() {
        for (UUID homeId : automationService.enabledHomeIds()) {
            try {
                automationService.runHomeCycle(homeId);
            } catch (Exception e) {
                log.error("Error en el ciclo de automatización de la casa {}: {}", homeId, e.getMessage(), e);
            }
        }
    }
}
