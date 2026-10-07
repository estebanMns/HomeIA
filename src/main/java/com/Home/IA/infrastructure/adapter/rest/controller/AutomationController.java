package com.home.ia.infrastructure.adapter.rest.controller;

import com.home.ia.application.port.in.AutomationResult;
import com.home.ia.application.port.in.SwitchOffVacantRoomsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automation")
@RequiredArgsConstructor
@Slf4j
public class AutomationController {

    private final SwitchOffVacantRoomsUseCase switchOffVacantRoomsUseCase;

    @PostMapping("/switch-off-vacant-rooms")
    public ResponseEntity<AutomationResult> switchOffVacantRooms() {
        log.info("Manual trigger: switch off vacant rooms");
        AutomationResult result = switchOffVacantRoomsUseCase.execute();
        log.info("Automation executed: {} sensors evaluated, {} devices switched off",
                result.sensorsEvaluated(), result.switchedOffDevices().size());
        return ResponseEntity.ok(result);
    }
}
