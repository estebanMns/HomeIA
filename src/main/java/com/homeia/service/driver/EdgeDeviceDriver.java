package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.edge.DeviceCommandService;
import com.homeia.persistence.DeviceEntity;

import java.util.Map;
import java.util.Optional;

/**
 * Adaptador común para los dispositivos que se controlan dentro de la LAN a través de HomeIA Edge.
 * El backend no habla HTTP/Zigbee/ESPHome con el dispositivo: envía una orden firmada al Edge de la casa,
 * que la ejecuta, la confirma (ack) y reporta el estado resultante.
 */
public abstract class EdgeDeviceDriver implements DeviceDriver {

    private final DeviceCommandService commands;
    private final DeviceProvider provider;

    protected EdgeDeviceDriver(DeviceCommandService commands, DeviceProvider provider) {
        this.commands = commands;
        this.provider = provider;
    }

    @Override
    public DeviceProvider provider() {
        return provider;
    }

    @Override
    public Optional<String> unavailableReason() {
        return Optional.empty();
    }

    @Override
    public Optional<String> unavailableReason(DeviceEntity device) {
        return commands.unavailableReason(device);
    }

    @Override
    public Optional<Map<String, Object>> send(DeviceEntity device, DeviceCommand command, Integer brightness,
                                              String requestedBy) {
        commands.dispatch(device, command, brightness, requestedBy);
        return Optional.empty(); // el estado llega con la confirmación del Edge
    }

    @Override
    public Optional<Map<String, Object>> readState(DeviceEntity device) {
        return Optional.empty(); // el Edge consulta y publica el estado periódicamente
    }
}
