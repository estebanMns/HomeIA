package com.home.ia.infrastructure.adapter.rest.controller;

import com.home.ia.infrastructure.adapter.rest.dto.DeviceDTO;
import com.home.ia.infrastructure.adapter.rest.dto.RoomDTO;
import com.home.ia.infrastructure.persistence.entity.DeviceEntity;
import com.home.ia.infrastructure.persistence.entity.RoomEntity;
import com.home.ia.infrastructure.persistence.repository.RoomJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Slf4j
public class RoomController {

    private final RoomJpaRepository roomRepository;

    @GetMapping
    public ResponseEntity<List<RoomDTO>> getAllRooms() {
        List<RoomDTO> rooms = roomRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomDTO> getRoomById(@PathVariable String id) {
        return roomRepository.findById(id)
                .map(room -> ResponseEntity.ok(toDTO(room)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/home/{homeId}")
    public ResponseEntity<List<RoomDTO>> getRoomsByHome(@PathVariable String homeId) {
        List<RoomDTO> rooms = roomRepository.findByHomeId(homeId).stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(rooms);
    }

    private RoomDTO toDTO(RoomEntity entity) {
        return RoomDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .homeId(entity.getHomeId())
                .devices(entity.getDevices().stream()
                        .map(this::toDeviceDTO)
                        .toList())
                .build();
    }

    private DeviceDTO toDeviceDTO(DeviceEntity device) {
        return DeviceDTO.builder()
                .id(device.getId())
                .name(device.getName())
                .roomId(device.getRoomId())
                .type(device.getType())
                .connectionStatus(device.getConnectionStatus())
                .powerState(device.getPowerState())
                .lastStatusChange(device.getLastStatusChange())
                .brightness(device.getBrightness())
                .consumption(device.getConsumption())
                .critical(device.getCritical())
                .presence(device.getPresence())
                .lastPresenceDetected(device.getLastPresenceDetected())
                .build();
    }
}
