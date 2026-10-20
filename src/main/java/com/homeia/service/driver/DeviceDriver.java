package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.persistence.DeviceEntity;

import java.util.Map;
import java.util.Optional;

/**
 * Puerto de salida hacia los dispositivos. Cada forma de conexión (MQTT, Tuya…) tiene su adaptador;
 * el resto del backend solo habla con esta interfaz, así agregar otra marca no toca la lógica.
 */
public interface DeviceDriver {

    DeviceProvider provider();

    /** Vacío si el driver puede enviar comandos ahora; si no, el motivo para mostrarlo al usuario. */
    Optional<String> unavailableReason();

    /**
     * Envía la orden al dispositivo. Lanza {@link DeviceDriverException} si no se pudo.
     * @param brightness 0–100, solo para {@link DeviceCommand#BRIGHTNESS}
     * @return el estado que la plataforma confirmó (Tuya confirma la orden al aceptarla), o vacío si el
     *         dispositivo lo reportará por su cuenta (MQTT)
     */
    Optional<Map<String, Object>> send(DeviceEntity device, DeviceCommand command, Integer brightness, String requestedBy);

    /**
     * Estado actual leído del dispositivo en el formato de HomeIA, si el driver puede consultarlo
     * (Tuya sí; en MQTT el dispositivo lo publica por su cuenta y esto devuelve vacío).
     */
    Optional<Map<String, Object>> readState(DeviceEntity device);
}
