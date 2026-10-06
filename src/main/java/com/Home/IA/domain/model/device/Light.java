package com.home.ia.domain.model.device;

import com.home.ia.domain.model.home.RoomId;

public class Light extends SwitchableDevice {

    public Light(DeviceId id, String name, RoomId roomId,
            ConnectionStatus connectionStatus, PowerState powerState,
            double ratedPowerWatts) {
        super(id, name, roomId, connectionStatus, powerState, ratedPowerWatts);
    }

    public static Light register(String name, RoomId roomId, double ratedPowerWatts) {
        return new Light(DeviceId.generate(), name, roomId,
                ConnectionStatus.OFFLINE, PowerState.OFF, ratedPowerWatts);
    }

    @Override
    public DeviceType getType() {
        return DeviceType.LIGHT;
    }
}