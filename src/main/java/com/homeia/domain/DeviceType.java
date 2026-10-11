package com.homeia.domain;

public enum DeviceType {
    LIGHT,
    SMART_OUTLET,
    PRESENCE_SENSOR;

    /** Dispositivos que la automatización puede apagar en una habitación vacía. */
    public boolean switchable() {
        return this == LIGHT || this == SMART_OUTLET;
    }
}
