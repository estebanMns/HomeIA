package com.home.ia.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.home.ia.infrastructure.persistence.entity.DomainEventEntity;
import com.home.ia.infrastructure.persistence.repository.DomainEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final DomainEventJpaRepository eventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void logDeviceAction(String userId, String deviceId, String deviceName, String action, String details) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("deviceId", deviceId);
            payload.put("deviceName", deviceName);
            payload.put("action", action);
            payload.put("details", details);

            DomainEventEntity event = DomainEventEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .eventType("DEVICE_ACTION")
                    .deviceId(deviceId)
                    .payload(objectMapper.writeValueAsString(payload))
                    .occurredAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            eventRepository.save(event);
            log.info("Auditoría DEVICE: usuario={}, dispositivo={}, acción={}", userId, deviceName, action);
        } catch (Exception e) {
            log.error("Error al registrar auditoría de dispositivo: {}", e.getMessage(), e);
        }
    }

    public void logRoomAction(String userId, String roomId, String roomName, String action, String details) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("roomId", roomId);
            payload.put("roomName", roomName);
            payload.put("action", action);
            payload.put("details", details);

            DomainEventEntity event = DomainEventEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .eventType("ROOM_ACTION")
                    .roomId(roomId)
                    .payload(objectMapper.writeValueAsString(payload))
                    .occurredAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            eventRepository.save(event);
            log.info("Auditoría ROOM: usuario={}, habitación={}, acción={}", userId, roomName, action);
        } catch (Exception e) {
            log.error("Error al registrar auditoría de habitación: {}", e.getMessage(), e);
        }
    }

    public void logAutomationAction(String userId, String automationId, String action, String details) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("automationId", automationId);
            payload.put("action", action);
            payload.put("details", details);

            DomainEventEntity event = DomainEventEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .eventType("AUTOMATION_ACTION")
                    .payload(objectMapper.writeValueAsString(payload))
                    .occurredAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            eventRepository.save(event);
            log.info("Auditoría AUTOMATION: usuario={}, acción={}", userId, action);
        } catch (Exception e) {
            log.error("Error al registrar auditoría de automatización: {}", e.getMessage(), e);
        }
    }

    public void logAlertAction(String userId, String alertId, String action, String details) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("alertId", alertId);
            payload.put("action", action);
            payload.put("details", details);

            DomainEventEntity event = DomainEventEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .eventType("ALERT_ACTION")
                    .payload(objectMapper.writeValueAsString(payload))
                    .occurredAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            eventRepository.save(event);
            log.info("Auditoría ALERT: usuario={}, acción={}", userId, action);
        } catch (Exception e) {
            log.error("Error al registrar auditoría de alerta: {}", e.getMessage(), e);
        }
    }
}
