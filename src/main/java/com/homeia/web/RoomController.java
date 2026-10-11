package com.homeia.web;

import com.homeia.persistence.RoomEntity;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.RoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    public record RoomDto(UUID id, String name, String location, Instant createdAt) {
        static RoomDto from(RoomEntity room) {
            return new RoomDto(room.getId(), room.getName(), room.getLocation(), room.getCreatedAt());
        }
    }

    public record RoomBody(@NotBlank String name, String location) {
    }

    public record UpdateRoomBody(String name, String location) {
    }

    public record PresenceBody(boolean present) {
    }

    @GetMapping
    public List<RoomDto> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return roomService.list(principal.homeId()).stream().map(RoomDto::from).toList();
    }

    @GetMapping("/{id}")
    public RoomDto get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return RoomDto.from(roomService.get(principal.homeId(), id));
    }

    @PostMapping
    public RoomDto create(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody RoomBody body) {
        return RoomDto.from(roomService.create(principal, body.name(), body.location()));
    }

    @PutMapping("/{id}")
    public RoomDto update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                          @RequestBody UpdateRoomBody body) {
        return RoomDto.from(roomService.update(principal, id, body.name(), body.location()));
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        roomService.delete(principal, id);
    }

    /** Registro manual de presencia (p. ej. desde un sensor o un botón de la app). */
    @PostMapping("/{id}/presence")
    public void presence(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                         @RequestBody PresenceBody body) {
        roomService.get(principal.homeId(), id);
        roomService.observePresence(principal.homeId(), id, body.present(), Instant.now());
    }
}
