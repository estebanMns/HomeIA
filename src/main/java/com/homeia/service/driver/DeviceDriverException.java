package com.homeia.service.driver;

/** No se pudo enviar la orden al dispositivo (broker caído, nube de Tuya sin respuesta, etc.). */
public class DeviceDriverException extends RuntimeException {
    public DeviceDriverException(String message, Throwable cause) {
        super(message, cause);
    }

    public DeviceDriverException(String message) {
        super(message);
    }
}
