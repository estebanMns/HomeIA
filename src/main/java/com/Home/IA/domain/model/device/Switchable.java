package com.home.ia.domain.model.device;

public interface Switchable {
    void turnOn();

    void turnOff();

    boolean isOn();

    double getRatedPowerWatts();
}