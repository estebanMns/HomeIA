package com.home.ia.domain.exception;

import com.home.ia.domain.model.device.DeviceId;

public class DeviceOfflineException extends DomainException {
    public DeviceOfflineException(DeviceId id) {
        super("El dispositivo " + id.value() + " está desconectado");
    }
}