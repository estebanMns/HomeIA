package com.homeia.integration.tuya;

/** La nube de Tuya respondió con error (success=false) o no respondió. */
public class TuyaApiException extends RuntimeException {

    private final int code;

    public TuyaApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public TuyaApiException(String message, Throwable cause) {
        super(message, cause);
        this.code = -1;
    }

    /** Código de error de Tuya (p. ej. 1010 token inválido, 1106 permiso denegado); -1 si no hubo respuesta. */
    public int code() {
        return code;
    }
}
