package com.home.ia.application.port.out;

import com.home.ia.domain.model.device.DeviceId;
import com.home.ia.domain.model.device.PowerState;

public interface DeviceCommandPort {
    void sendPowerCommand(DeviceId deviceId, PowerState desiredState);
}