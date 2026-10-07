package com.home.ia.application.port.in;

import com.home.ia.domain.model.device.DeviceId;

import java.util.List;

public record AutomationResult(int sensorsEvaluated, List<DeviceId> switchedOffDevices) {
    public AutomationResult {
        switchedOffDevices = List.copyOf(switchedOffDevices);
    }
}