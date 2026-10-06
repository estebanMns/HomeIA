package com.home.ia.domain.model.device;

import com.home.ia.domain.model.home.RoomId;

public abstract class SwitchableDevice extends Device implements Switchable {
    private PowerState powerState;
    private final double ratedPowerWatts;

    protected SwitchableDevice(DeviceId id, String name, RoomId roomId,
            ConnectionStatus connectionStatus,
            PowerState powerState, double ratedPowerWatts) {
        super(id, name, roomId, connectionStatus);
        if (ratedPowerWatts < 0) {
            throw new IllegalArgumentException("La potencia no puede ser negativa");
        }
        this.powerState = powerState == null ? PowerState.OFF : powerState;
        this.ratedPowerWatts = ratedPowerWatts;
    }

    @Override
    public void turnOn() {
        ensureOnline();
        this.powerState = PowerState.ON;
    }

    @Override
    public void turnOff() {
        ensureOnline();
        this.powerState = PowerState.OFF;
    }

    @Override
    public boolean isOn() {
        return powerState == PowerState.ON;
    }

    @Override
    public double getRatedPowerWatts() {
        return ratedPowerWatts;
    }

    public PowerState getPowerState() {
        return powerState;
    }
}