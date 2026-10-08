package com.home.ia.infrastructure.adapter.rest.controller;

import com.home.ia.application.service.AuditService;
import com.home.ia.infrastructure.adapter.rest.dto.DeviceDTO;
import com.home.ia.infrastructure.persistence.entity.DeviceEntity;
import com.home.ia.infrastructure.persistence.repository.DeviceJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {

    private final DeviceJpaRepository deviceRepository;
    private final AuditService auditService;
    private static final String SYSTEM_USER = "SYSTEM";

    @GetMapping
    public ResponseEntity<List<DeviceDTO>> getAllDevices() {
        log.info("GET /api/devices - Obteniendo todos los dispositivos");
        try {
            var devices = deviceRepository.findAll();
            log.debug("Se encontraron {} dispositivos", devices.size());
            return ResponseEntity.ok(devices.stream()
                    .map(this::toDTO)
                    .toList());
        } catch (Exception e) {
            log.error("Error al obtener dispositivos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<DeviceDTO>> getDevicesByRoom(@PathVariable String roomId) {
        log.info("GET /api/devices/room/{} - Obteniendo dispositivos de habitación", roomId);
        try {
            var devices = deviceRepository.findByRoomId(roomId);
            log.debug("Se encontraron {} dispositivos en la habitación {}", devices.size(), roomId);
            return ResponseEntity.ok(devices.stream()
                    .map(this::toDTO)
                    .toList());
        } catch (Exception e) {
            log.error("Error al obtener dispositivos de habitación {}: {}", roomId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceDTO> getDevice(@PathVariable String id) {
        log.info("GET /api/devices/{} - Obteniendo dispositivo", id);
        try {
            var device = deviceRepository.findById(id);
            if (device.isPresent()) {
                log.debug("Dispositivo {} encontrado", id);
                return ResponseEntity.ok(toDTO(device.get()));
            } else {
                log.warn("Dispositivo {} no encontrado", id);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error al obtener dispositivo {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/toggle")
    public ResponseEntity<DeviceDTO> toggleDevice(@PathVariable String id) {
        log.info("POST /api/devices/{}/toggle - Alternando estado del dispositivo", id);
        try {
            var device = deviceRepository.findById(id);
            if (device.isEmpty()) {
                log.warn("Dispositivo {} no encontrado", id);
                return ResponseEntity.notFound().build();
            }

            var deviceEntity = device.get();
            boolean newState = deviceEntity.getPowerState() == null ||
                             !deviceEntity.getPowerState().toString().equals("ON");

            log.debug("Dispositivo {}: cambiando de {} a {}",
                    id, deviceEntity.getPowerState(), newState ? "ON" : "OFF");

            auditService.logDeviceAction(SYSTEM_USER, id, deviceEntity.getName(),
                    newState ? "TURN_ON" : "TURN_OFF",
                    "Toggle automático desde API REST");

            return ResponseEntity.ok(toDTO(deviceEntity));
        } catch (Exception e) {
            log.error("Error al alternar dispositivo {}: {}", id, e.getMessage(), e);
            auditService.logDeviceAction(SYSTEM_USER, id, "UNKNOWN", "TOGGLE_ERROR",
                    "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/turn-on")
    public ResponseEntity<DeviceDTO> turnOnDevice(@PathVariable String id) {
        log.info("POST /api/devices/{}/turn-on - Encendiendo dispositivo", id);
        try {
            var device = deviceRepository.findById(id);
            if (device.isEmpty()) {
                log.warn("Dispositivo {} no encontrado", id);
                return ResponseEntity.notFound().build();
            }

            var deviceEntity = device.get();
            log.debug("Encendiendo dispositivo: {}", deviceEntity.getName());

            auditService.logDeviceAction(SYSTEM_USER, id, deviceEntity.getName(),
                    "TURN_ON", "Encendido desde API REST");

            return ResponseEntity.ok(toDTO(deviceEntity));
        } catch (Exception e) {
            log.error("Error al encender dispositivo {}: {}", id, e.getMessage(), e);
            auditService.logDeviceAction(SYSTEM_USER, id, "UNKNOWN", "TURN_ON_ERROR",
                    "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/turn-off")
    public ResponseEntity<DeviceDTO> turnOffDevice(@PathVariable String id) {
        log.info("POST /api/devices/{}/turn-off - Apagando dispositivo", id);
        try {
            var device = deviceRepository.findById(id);
            if (device.isEmpty()) {
                log.warn("Dispositivo {} no encontrado", id);
                return ResponseEntity.notFound().build();
            }

            var deviceEntity = device.get();
            log.debug("Apagando dispositivo: {}", deviceEntity.getName());

            auditService.logDeviceAction(SYSTEM_USER, id, deviceEntity.getName(),
                    "TURN_OFF", "Apagado desde API REST");

            return ResponseEntity.ok(toDTO(deviceEntity));
        } catch (Exception e) {
            log.error("Error al apagar dispositivo {}: {}", id, e.getMessage(), e);
            auditService.logDeviceAction(SYSTEM_USER, id, "UNKNOWN", "TURN_OFF_ERROR",
                    "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
