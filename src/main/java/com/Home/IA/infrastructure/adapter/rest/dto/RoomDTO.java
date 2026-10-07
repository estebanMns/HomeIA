package com.home.ia.infrastructure.adapter.rest.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomDTO {
    private String id;
    private String name;
    private String homeId;
    private List<DeviceDTO> devices;
    private Instant createdAt;
}
