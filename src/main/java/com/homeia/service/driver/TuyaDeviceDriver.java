package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.domain.PowerState;
import com.homeia.integration.tuya.TuyaApiException;
import com.homeia.integration.tuya.TuyaDataPoint;
import com.homeia.integration.tuya.TuyaGateway;
import com.homeia.integration.tuya.TuyaScale;
import com.homeia.integration.tuya.TuyaStateMapper;
import com.homeia.persistence.DeviceEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Dispositivos Tuya / Smart Life, controlados por la nube de Tuya. */
@Component
public class TuyaDeviceDriver implements DeviceDriver {

    private final TuyaGateway tuya;

    public TuyaDeviceDriver(TuyaGateway tuya) {
        this.tuya = tuya;
    }

    @Override
    public DeviceProvider provider() {
        return DeviceProvider.TUYA;
    }

    @Override
    public Optional<String> unavailableReason() {
        return tuya.configured() ? Optional.empty()
                : Optional.of("Tuya no está configurado: agrega TUYA_ACCESS_ID y TUYA_ACCESS_SECRET al .env y reinicia el backend");
    }

    @Override
    public Optional<Map<String, Object>> send(DeviceEntity device, DeviceCommand command, Integer brightness,
                                              String requestedBy) {
        if (command == DeviceCommand.STATUS) {
            return readState(device);
        }
        String id = externalId(device);
        try {
            List<TuyaDataPoint> current = tuya.knownStatus(id);
            Map<String, TuyaScale> spec = tuya.specification(id);
            TuyaDataPoint order = switch (command) {
                case ON -> TuyaStateMapper.power(powerCode(current, device), true);
                case OFF -> TuyaStateMapper.power(powerCode(current, device), false);
                case TOGGLE -> TuyaStateMapper.power(powerCode(current, device), device.getPowerState() != PowerState.ON);
                case BRIGHTNESS -> TuyaStateMapper.brightness(TuyaStateMapper.brightnessCode(current)
                                .orElseThrow(() -> new DeviceDriverException(device.getName() + " no permite ajustar el brillo")),
                        brightness == null ? 100 : brightness, spec);
                case STATUS -> throw new IllegalStateException();
            };
            tuya.sendCommands(id, List.of(order));
            // Tuya responde success solo cuando aceptó la orden: ese es el estado confirmado.
            return Optional.of(order.value() instanceof Boolean on
                    ? Map.of("powerState", on ? "ON" : "OFF")
                    : Map.of("brightness", brightness == null ? 100 : brightness));
        } catch (TuyaApiException e) {
            throw new DeviceDriverException(e.getMessage(), e);
        }
    }

    @Override
    public Optional<Map<String, Object>> readState(DeviceEntity device) {
        String id = externalId(device);
        try {
            return Optional.of(TuyaStateMapper.toReport(tuya.status(id), tuya.specification(id)));
        } catch (TuyaApiException e) {
            throw new DeviceDriverException(e.getMessage(), e);
        }
    }

    private static String powerCode(List<TuyaDataPoint> current, DeviceEntity device) {
        return TuyaStateMapper.powerCode(current)
                .orElseThrow(() -> new DeviceDriverException(device.getName() + " no tiene un interruptor que HomeIA reconozca"));
    }

    private static String externalId(DeviceEntity device) {
        if (device.getExternalId() == null || device.getExternalId().isBlank()) {
            throw new DeviceDriverException(device.getName() + " no tiene el id de Tuya asociado");
        }
        return device.getExternalId();
    }
}
