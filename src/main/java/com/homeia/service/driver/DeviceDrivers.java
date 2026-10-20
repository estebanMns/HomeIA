package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.persistence.DeviceEntity;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Elige el driver de cada dispositivo según por dónde se conecta. */
@Component
public class DeviceDrivers {

    private final Map<DeviceProvider, DeviceDriver> byProvider = new EnumMap<>(DeviceProvider.class);

    public DeviceDrivers(List<DeviceDriver> drivers) {
        drivers.forEach(d -> byProvider.put(d.provider(), d));
    }

    public DeviceDriver forDevice(DeviceEntity device) {
        DeviceProvider provider = device.getProvider() == null ? DeviceProvider.MQTT : device.getProvider();
        DeviceDriver driver = byProvider.get(provider);
        if (driver == null) {
            throw new DeviceDriverException("No hay driver para dispositivos " + provider);
        }
        return driver;
    }
}
