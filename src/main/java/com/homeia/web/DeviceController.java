package com.homeia.web;

import com.homeia.domain.ConnectionStatus;
import com.homeia.domain.DeviceProvider;
import com.homeia.domain.DeviceType;
import com.homeia.domain.PowerState;
import com.homeia.persistence.DeviceEntity;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.DeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.homeia.domain.CommandStatus;
import com.homeia.edge.DeviceCommandService;
import com.homeia.edge.EdgeInboundService;
import com.homeia.service.ApiException;
import com.homeia.service.DeviceCapabilityPolicy;
import com.homeia.service.DeviceCredentialService;
import com.homeia.service.DeviceEventService;
import com.homeia.service.RateLimiter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceEventService deviceEvents;
    private final DeviceCommandService commands;
    private final DeviceCredentialService credentials;
    private final EdgeInboundService edgeInbound;
    private final RateLimiter commandLimiter = new RateLimiter(60, Duration.ofMinutes(1));

    public DeviceController(DeviceService deviceService, DeviceEventService deviceEvents, DeviceCommandService commands,
                            DeviceCredentialService credentials, EdgeInboundService edgeInbound) {
        this.deviceService = deviceService;
        this.deviceEvents = deviceEvents;
        this.commands = commands;
        this.credentials = credentials;
        this.edgeInbound = edgeInbound;
    }

    public record DeviceDto(UUID id, String name, UUID roomId, DeviceType type, ConnectionStatus connectionStatus,
                            PowerState powerState, Integer brightness, double consumptionWatts, boolean critical,
                            Boolean presence, Instant lastPresenceAt, Instant lastStatusChangeAt, Instant lastSeenAt,
                            DeviceProvider provider, String externalId, Double consumptionAlertWatts,
                            com.homeia.domain.DeviceProtocol protocol, String manufacturer, String model, String networkAddress,
                            String capabilitiesJson, UUID edgeId, String telemetryJson, Instant telemetryAt,
                            List<String> effectiveCapabilities) {
        static DeviceDto from(DeviceEntity d) {
            return new DeviceDto(d.getId(), d.getName(), d.getRoomId(), d.getType(), d.getConnectionStatus(),
                    d.getPowerState(), d.getBrightness(), d.getConsumptionWatts(), d.isCritical(), d.getPresence(),
                    d.getLastPresenceAt(), d.getLastStatusChangeAt(), d.getLastSeenAt(), d.getProvider(), d.getExternalId(),
                    d.getConsumptionAlertWatts(), d.getProtocol(), d.getManufacturer(), d.getModel(), d.getNetworkAddress(),
                    d.getCapabilitiesJson(), d.getEdgeId(), d.getTelemetryJson(), d.getTelemetryAt(),
                    DeviceCapabilityPolicy.effective(d.getType(), d.getProvider(), DeviceService.capabilities(d))
                            .stream().map(Enum::name).sorted().toList());
        }
    }

    public record DeviceBody(@NotBlank String name, @NotNull DeviceType type, UUID roomId, Boolean critical) {
    }

    /** consumptionAlertWatts: umbral propio del dispositivo; null no lo cambia, 0 o negativo vuelve al de la casa. */
    public record UpdateDeviceBody(String name, UUID roomId, Boolean clearRoom, Boolean critical,
                                   Double consumptionAlertWatts, DeviceType type, String manufacturer, String model,
                                   String networkAddress, String externalId, List<String> capabilities) {
    }

    /** Campos vacíos o nulos = sin credencial; todos vacíos borra las credenciales. */
    public record CredentialsBody(@Size(max = 64) String username, @Size(max = 128) String password,
                                  @Size(max = 128) String encryptionKey) {
    }

    public record DeviceEventDto(UUID id, String type, String details, UUID edgeId, Instant createdAt) {
    }

    public record DeviceCommandDto(UUID id, String command, Integer brightness, CommandStatus status, int attempts,
                                   String error, String requestedBy, Instant createdAt, Instant completedAt) {
    }

    public record CommandBody(@NotNull DeviceService.Command command, Integer brightness, Boolean confirmCritical) {
    }

    @GetMapping
    public List<DeviceDto> list(@AuthenticationPrincipal AuthPrincipal principal,
                                @RequestParam(required = false) UUID roomId,
                                @RequestParam(required = false) DeviceType type,
                                @RequestParam(required = false) ConnectionStatus connection) {
        return deviceService.list(principal.homeId(), roomId, type, connection).stream().map(DeviceDto::from).toList();
    }

    @GetMapping("/{id}")
    public DeviceDto get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return DeviceDto.from(deviceService.get(principal.homeId(), id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public DeviceDto create(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody DeviceBody body) {
        return DeviceDto.from(deviceService.create(principal, body.name(), body.type(), body.roomId(),
                Boolean.TRUE.equals(body.critical())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public DeviceDto update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                            @RequestBody UpdateDeviceBody body) {
        DeviceEntity device = deviceService.update(principal, id, new DeviceService.DeviceChanges(body.name(), body.roomId(),
                Boolean.TRUE.equals(body.clearRoom()), body.critical(), body.consumptionAlertWatts(), body.type(),
                body.manufacturer(), body.model(), body.networkAddress(), body.externalId(), body.capabilities()));
        edgeInbound.resyncForDevice(device);
        return DeviceDto.from(device);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/credentials")
    public Map<String, Object> credentialsStatus(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        deviceService.get(principal.homeId(), id);
        return Map.of("configured", credentials.has(id));
    }

    /** Guarda credenciales locales cifradas. Nunca se devuelven: solo se informa si existen. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/credentials")
    public Map<String, Object> setCredentials(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                              @Valid @RequestBody CredentialsBody body) {
        deviceService.setCredentials(principal, id,
                new DeviceCredentialService.Credentials(body.username(), body.password(), body.encryptionKey()));
        edgeInbound.resyncForDevice(deviceService.get(principal.homeId(), id));
        return Map.of("configured", credentials.has(id));
    }

    @GetMapping("/{id}/events")
    public List<DeviceEventDto> events(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                       @RequestParam(defaultValue = "100") int limit) {
        deviceService.get(principal.homeId(), id);
        return deviceEvents.forDevice(principal.homeId(), id, limit).stream()
                .map(e -> new DeviceEventDto(e.getId(), e.getType().name(), e.getDetails(), e.getEdgeId(), e.getCreatedAt()))
                .toList();
    }

    @GetMapping("/{id}/commands")
    public List<DeviceCommandDto> commandHistory(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                                 @RequestParam(defaultValue = "30") int limit) {
        deviceService.get(principal.homeId(), id);
        return commands.recent(principal.homeId(), id, limit).stream()
                .map(c -> new DeviceCommandDto(c.getId(), c.getCommand(), c.getBrightness(), c.getStatus(), c.getAttempts(),
                        c.getError(), c.getRequestedBy(), c.getCreatedAt(), c.getCompletedAt()))
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        deviceService.delete(principal, id);
    }

    /** Envía ON / OFF / TOGGLE / BRIGHTNESS / STATUS por el driver del dispositivo (MQTT o Tuya). */
    @PostMapping("/{id}/commands")
    public DeviceDto command(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                             @Valid @RequestBody CommandBody body) {
        if (!commandLimiter.tryAcquire(principal.userId().toString())) {
            throw ApiException.tooManyRequests("Demasiadas órdenes seguidas; espera un momento");
        }
        return DeviceDto.from(deviceService.command(principal, id, body.command(), body.brightness(),
                Boolean.TRUE.equals(body.confirmCritical())));
    }
}
