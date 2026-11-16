package com.homeia.service.driver;

import com.homeia.domain.DeviceProvider;
import com.homeia.edge.DeviceCommandService;
import org.springframework.stereotype.Component;

/** TASMOTA a través de HomeIA Edge (ver edge/homeia_edge/adapters). */
@Component
public class TasmotaDeviceDriver extends EdgeDeviceDriver {
    public TasmotaDeviceDriver(DeviceCommandService commands) {
        super(commands, DeviceProvider.TASMOTA);
    }
}
