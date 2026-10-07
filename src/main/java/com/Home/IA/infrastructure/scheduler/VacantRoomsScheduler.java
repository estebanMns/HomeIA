package com.home.ia.infrastructure.scheduler;

import com.home.ia.application.port.in.SwitchOffVacantRoomsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class VacantRoomsScheduler {

    private final SwitchOffVacantRoomsUseCase switchOffVacantRoomsUseCase;

    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void checkAndSwitchOffVacantRooms() {
        log.debug("Scheduled task: checking vacant rooms...");
        try {
            var result = switchOffVacantRoomsUseCase.execute();
            log.info("Vacant rooms check completed: {} sensors evaluated, {} devices switched off",
                    result.sensorsEvaluated(), result.switchedOffDevices().size());
        } catch (Exception e) {
            log.error("Error during vacant rooms check", e);
        }
    }
}
