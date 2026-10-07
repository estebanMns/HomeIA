package com.home.ia.infrastructure.adapter.rest.dto;

import com.home.ia.domain.model.device.ConnectionStatus;
import com.home.ia.domain.model.device.DeviceType;
import com.home.ia.domain.model.device.PowerState;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceDTO {
    private String id;
    private String name;
    private String roomId;
    private DeviceType type;
    private ConnectionStatus connectionStatus;
    private PowerState powerState;
    private Instant lastStatusChange;
    private Integer brightness;
    private Double consumption;
    private Boolean critical;
    private Boolean presence;
    private Instant lastPresenceDetected;
}
