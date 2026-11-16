package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.edge.DeviceCommandService;
import org.springframework.stereotype.Component;

/** WLED a través de HomeIA Edge (ver edge/homeia_edge/adapters). */
@Component
public class WledDeviceDriver extends EdgeDeviceDriver {
    public WledDeviceDriver(DeviceCommandService commands) {
        super(commands, DeviceProvider.WLED);
    }
}
