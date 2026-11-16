package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.edge.DeviceCommandService;
import org.springframework.stereotype.Component;

/** ESPHOME a través de HomeIA Edge (ver edge/homeia_edge/adapters). */
@Component
public class EsphomeDeviceDriver extends EdgeDeviceDriver {
    public EsphomeDeviceDriver(DeviceCommandService commands) {
        super(commands, DeviceProvider.ESPHOME);
    }
}
