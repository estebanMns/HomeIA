package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.edge.DeviceCommandService;
import org.springframework.stereotype.Component;

/** SHELLY a través de HomeIA Edge (ver edge/homeia_edge/adapters). */
@Component
public class ShellyDeviceDriver extends EdgeDeviceDriver {
    public ShellyDeviceDriver(DeviceCommandService commands) {
        super(commands, DeviceProvider.SHELLY);
    }
}
