package com.homeia.service;

import com.homeia.domain.DeviceType;
import com.homeia.persistence.DeviceEntity;
import com.homeia.persistence.DeviceRepository;
import com.homeia.persistence.PresenceEventEntity;
import com.homeia.persistence.PresenceEventRepository;
import com.homeia.persistence.RoomEntity;
import com.homeia.persistence.RoomRepository;
import com.homeia.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final DeviceRepository deviceRepository;
    private final PresenceEventRepository presenceRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RoomEntity> list(UUID homeId) {
        return roomRepository.findByHomeIdOrderByNameAsc(homeId);
    }

    @Transactional(readOnly = true)
    public RoomEntity get(UUID homeId, UUID roomId) {
        return roomRepository.findByIdAndHomeId(roomId, homeId).orElseThrow(() -> ApiException.notFound("Habitación"));
    }

    @Transactional
    public RoomEntity create(AuthPrincipal principal, String name, String location) {
        String cleanName = requireName(name);
        if (roomRepository.existsByHomeIdAndNameIgnoreCase(principal.homeId(), cleanName)) {
            throw ApiException.conflict("Ya existe una habitación llamada " + cleanName);
        }
        RoomEntity room = new RoomEntity();
        room.setHomeId(principal.homeId());
        room.setName(cleanName);
        room.setLocation(blankToNull(location));
        roomRepository.save(room);
        auditService.record(principal, "ROOM_CREATED", "ROOM", room.getId().toString(), cleanName);
        return room;
    }

    @Transactional
    public RoomEntity update(AuthPrincipal principal, UUID roomId, String name, String location) {
        RoomEntity room = get(principal.homeId(), roomId);
        if (name != null) {
            String cleanName = requireName(name);
            if (!cleanName.equalsIgnoreCase(room.getName())
                    && roomRepository.existsByHomeIdAndNameIgnoreCase(principal.homeId(), cleanName)) {
                throw ApiException.conflict("Ya existe una habitación llamada " + cleanName);
            }
            room.setName(cleanName);
        }
        if (location != null) {
            room.setLocation(blankToNull(location));
        }
        room.setUpdatedAt(Instant.now());
        auditService.record(principal, "ROOM_UPDATED", "ROOM", roomId.toString(), room.getName());
        return room;
    }

    @Transactional
    public void delete(AuthPrincipal principal, UUID roomId) {
        RoomEntity room = get(principal.homeId(), roomId);
        roomRepository.delete(room);
        auditService.record(principal, "ROOM_DELETED", "ROOM", roomId.toString(), room.getName());
    }

    /**
     * Registra presencia en una habitación. Solo guarda un evento cuando el estado cambia,
     * así el historial sirve para estimar cuándo se quedó vacía.
     */
    @Transactional
    public void observePresence(UUID homeId, UUID roomId, boolean present, Instant observedAt) {
        Optional<PresenceEventEntity> last = presenceRepository.findFirstByRoomIdOrderByObservedAtDesc(roomId);
        if (last.isPresent() && last.get().isPresent() == present) {
            return;
        }
        presenceRepository.save(new PresenceEventEntity(homeId, roomId, present, observedAt));
        if (present) {
            for (DeviceEntity sensor : deviceRepository.findByRoomId(roomId)) {
                if (sensor.getType() == DeviceType.PRESENCE_SENSOR) {
                    sensor.setPresence(true);
                    sensor.setLastPresenceAt(observedAt);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public Optional<PresenceEventEntity> latestPresence(UUID roomId) {
        return presenceRepository.findFirstByRoomIdOrderByObservedAtDesc(roomId);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("El nombre de la habitación es obligatorio");
        }
        return name.trim();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
