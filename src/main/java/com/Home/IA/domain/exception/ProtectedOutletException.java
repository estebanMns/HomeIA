package com.home.ia.domain.exception;

import com.home.ia.domain.model.device.DeviceId;

public class ProtectedOutletException extends DomainException {
    public ProtectedOutletException(DeviceId id) {
        super("El tomacorriente " + id.value()
                + " es crítico y no puede deshabilitarse automáticamente");
    }
}