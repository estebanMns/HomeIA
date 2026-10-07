package com.home.ia.domain.model.device;

import com.home.ia.domain.model.home.RoomId;

public class SmartOutlet extends SwitchableDevice {
    private boolean critical;

    public SmartOutlet(DeviceId id, String name, RoomId roomId,
            ConnectionStatus connectionStatus, PowerState powerState,
            double ratedPowerWatts, boolean critical) {
        super(id, name, roomId, connectionStatus, powerState, ratedPowerWatts);
        this.critical = critical;
    }

    public static SmartOutlet register(String name, RoomId roomId,
            double ratedPowerWatts, boolean critical) {
        return new SmartOutlet(DeviceId.generate(), name, roomId,
                ConnectionStatus.OFFLINE, PowerState.OFF, ratedPowerWatts, critical);
    }

    public void markAsCritical() {
        this.critical = true;
    }

    public void unmarkAsCritical() {
        this.critical = false;
    }

    public boolean canBeDisabledAutomatically() {
        return !critical;
    }

    public boolean isCritical() {
        return critical;
    }

    @Override
    public DeviceType getType() {
        return DeviceType.SMART_OUTLET;
    }
}