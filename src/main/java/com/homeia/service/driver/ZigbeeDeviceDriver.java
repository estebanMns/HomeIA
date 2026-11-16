package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.edge.DeviceCommandService;
import org.springframework.stereotype.Component;

/** ZIGBEE a través de HomeIA Edge (ver edge/homeia_edge/adapters). */
@Component
public class ZigbeeDeviceDriver extends EdgeDeviceDriver {
    public ZigbeeDeviceDriver(DeviceCommandService commands) {
        super(commands, DeviceProvider.ZIGBEE);
    }
}
