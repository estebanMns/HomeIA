package com.home.ia.infrastructure.adapter.rest.controller;

import com.home.ia.infrastructure.adapter.rest.dto.DeviceDTO;
import com.home.ia.infrastructure.persistence.entity.DeviceEntity;
import com.home.ia.infrastructure.persistence.repository.DeviceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceJpaRepository deviceRepository;

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<DeviceDTO>> getDevicesByRoom(@PathVariable String roomId) {
        var devices = deviceRepository.findByRoomId(roomId);
        return ResponseEntity.ok(devices.stream()
                .map(this::toDTO)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceDTO> getDevice(@PathVariable String id) {
        return deviceRepository.findById(id)
                .map(device -> ResponseEntity.ok(toDTO(device)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private DeviceDTO toDTO(DeviceEntity entity) {
        return DeviceDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .roomId(entity.getRoomId())
                .type(entity.getType())
                .connectionStatus(entity.getConnectionStatus())
                .powerState(entity.getPowerState())
                .lastStatusChange(entity.getLastStatusChange())
                .brightness(entity.getBrightness())
                .consumption(entity.getConsumption())
                .critical(entity.getCritical())
                .presence(entity.getPresence())
                .lastPresenceDetected(entity.getLastPresenceDetected())
                .build();
    }
}
