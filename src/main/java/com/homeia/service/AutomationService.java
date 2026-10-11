package com.homeia.service;

import com.homeia.domain.ActionType;
import com.homeia.domain.AlertSeverity;
import com.homeia.domain.AlertType;
import com.homeia.domain.ConnectionStatus;
import com.homeia.domain.PowerState;
import com.homeia.integration.GeminiClient;
import com.homeia.persistence.AutomationActionEntity;
import com.homeia.persistence.AutomationActionRepository;
import com.homeia.persistence.AutomationSettingsEntity;
import com.homeia.persistence.AutomationSettingsRepository;
import com.homeia.persistence.DeviceEntity;
import com.homeia.persistence.DeviceRepository;
import com.homeia.persistence.PresenceEventEntity;
import com.homeia.persistence.PresenceEventRepository;
import com.homeia.persistence.RoomEntity;
import com.homeia.persistence.RoomRepository;
import com.homeia.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Motor de automatización por habitación:
 * 1. Si la habitación lleva "vacancyMinutes" sin presencia, apaga luces y tomacorrientes no críticos,
 *    salvo que la probabilidad de regreso supere la sensibilidad configurada.
 * 2. Si un dispositivo crítico sigue encendido en una habitación vacía durante "leakMinutes", alerta de posible fuga.
 * 3. Si un dispositivo supera "consumptionAlertWatts", alerta de consumo excesivo.
 * 4. Si un dispositivo deja de reportar, alerta de dispositivo sin conexión.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationService {

    private static final Duration PREDICTION_TTL = Duration.ofMinutes(10);
    private static final Duration HISTORY_WINDOW = Duration.ofDays(14);
    private static final Duration OFFLINE_AFTER = Duration.ofMinutes(5);
    private static final Duration PENDING_COMMAND_WINDOW = Duration.ofMinutes(2);

    private final AutomationSettingsRepository settingsRepository;
    private final AutomationActionRepository actionRepository;
    private final RoomRepository roomRepository;
    private final DeviceRepository deviceRepository;
    private final PresenceEventRepository presenceRepository;
    private final DeviceService deviceService;
    private final AlertService alertService;
    private final RoomService roomService;
    private final GeminiClient geminiClient;
    private final AuditService auditService;
    private final com.homeia.persistence.HomeRepository homeRepository;

    private final Map<UUID, Map.Entry<Instant, Double>> predictionCache = new HashMap<>();

    public record Settings(boolean enabled, int vacancyMinutes, int returnSensitivityPercent,
                           double consumptionAlertWatts, int leakMinutes) {
    }

    public record Status(boolean enabled, long decisionsLast24h, long actionsTotal, Instant lastDecisionAt,
                         String predictionMode, boolean geminiConfigured) {
    }

    // ---------- Configuración ----------

    @Transactional(readOnly = true)
    public Optional<AutomationSettingsEntity> settings(UUID homeId) {
        return settingsRepository.findById(homeId);
    }

    @Transactional
    public AutomationSettingsEntity updateSettings(AuthPrincipal principal, Settings input) {
        validate(input);
        AutomationSettingsEntity entity = settingsRepository.findById(principal.homeId())
                .orElseThrow(() -> ApiException.notFound("Configuración de automatización"));
        entity.setEnabled(input.enabled());
        entity.setVacancyMinutes(input.vacancyMinutes());
        entity.setReturnSensitivityPercent(input.returnSensitivityPercent());
        entity.setConsumptionAlertWatts(input.consumptionAlertWatts());
        entity.setLeakMinutes(input.leakMinutes());
        entity.setUpdatedAt(Instant.now());
        auditService.record(principal, "AUTOMATION_SETTINGS_UPDATED", "AUTOMATION", principal.homeId().toString(),
                "vacancy=" + input.vacancyMinutes() + "min, sensibilidad=" + input.returnSensitivityPercent()
                        + "%, umbral=" + input.consumptionAlertWatts() + "W, fuga=" + input.leakMinutes() + "min");
        return entity;
    }

    @Transactional
    public void setEnabled(AuthPrincipal principal, boolean enabled) {
        AutomationSettingsEntity entity = settingsRepository.findById(principal.homeId())
                .orElseThrow(() -> ApiException.notFound("Configuración de automatización"));
        entity.setEnabled(enabled);
        entity.setUpdatedAt(Instant.now());
        auditService.record(principal, enabled ? "AUTOMATION_ENABLED" : "AUTOMATION_DISABLED", "AUTOMATION",
                principal.homeId().toString(), null);
    }

    @Transactional(readOnly = true)
    public Status status(UUID homeId) {
        AutomationSettingsEntity settings = settingsRepository.findById(homeId).orElse(null);
        Instant since = Instant.now().minus(Duration.ofHours(24));
        List<AutomationActionEntity> recent = actionRepository.findByHomeIdAndCreatedAtAfter(homeId, since);
        Instant last = recent.stream().map(AutomationActionEntity::getCreatedAt).max(Instant::compareTo).orElse(null);
        return new Status(
                settings != null && settings.isEnabled(),
                recent.size(),
                actionRepository.findByHomeIdOrderByCreatedAtDesc(homeId, PageRequest.of(0, 1)).getTotalElements(),
                last,
                geminiClient.configured() ? "GEMINI" : "HISTORICO",
                geminiClient.configured());
    }

    @Transactional(readOnly = true)
    public Page<AutomationActionEntity> actions(UUID homeId, int page, int size) {
        return actionRepository.findByHomeIdOrderByCreatedAtDesc(homeId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /** Ejecuta un ciclo de automatización de la casa ahora mismo (botón "Ejecutar ahora"). */
    @Transactional
    public int runNow(AuthPrincipal principal) {
        // Mismo ciclo que el automático, ejecutado en la transacción de esta petición.
        AutomationSettingsEntity settings = settingsRepository.findById(principal.homeId())
                .orElseThrow(() -> ApiException.notFound("Configuración de automatización"));
        int actions = runCycle(settings, true);
        auditService.record(principal, "AUTOMATION_RUN", "AUTOMATION", principal.homeId().toString(),
                actions + " acciones");
        return actions;
    }

    /** Probabilidad (0-100) de que alguien regrese a la habitación en los próximos minutos. */
    @Transactional(readOnly = true)
    public double returnProbabilityPercent(RoomEntity room) {
        Instant now = Instant.now();
        Map.Entry<Instant, Double> cached = predictionCache.get(room.getId());
        if (cached != null && cached.getKey().plus(PREDICTION_TTL).isAfter(now)) {
            return cached.getValue();
        }
        double probability = geminiClient.configured()
                ? geminiProbability(room, now).orElseGet(() -> historicalProbability(room, now))
                : historicalProbability(room, now);
        predictionCache.put(room.getId(), Map.entry(now, probability));
        return probability * 100.0;
    }

    /** Probabilidad histórica: fracción de veces que la habitación estuvo ocupada a esta misma hora. */
    private double historicalProbability(RoomEntity room, Instant now) {
        Instant from = now.minus(HISTORY_WINDOW);
        List<PresenceEventEntity> events = presenceRepository.findByRoomIdAndObservedAtBetween(room.getId(), from, now);
        ZoneId zone = ZoneId.of(homeRepository.findById(room.getHomeId()).orElseThrow().getTimezone());
        int targetHour = ZonedDateTime.ofInstant(now, zone).getHour();

        // Tiempo ocupado vs. tiempo observado dentro de la misma franja horaria, día a día.
        double occupied = 0;
        double observed = 0;
        for (int i = 0; i < events.size(); i++) {
            PresenceEventEntity event = events.get(i);
            Instant end = i + 1 < events.size() ? events.get(i + 1).getObservedAt() : now;
            if (ZonedDateTime.ofInstant(event.getObservedAt(), zone).getHour() != targetHour) {
                continue;
            }
            double minutes = Duration.between(event.getObservedAt(), end).toSeconds() / 60.0;
            observed += minutes;
            if (event.isPresent()) {
                occupied += minutes;
            }
        }
        return observed <= 0 ? 0 : Math.min(1, occupied / observed);
    }

    private Optional<Double> geminiProbability(RoomEntity room, Instant now) {
        try {
            Instant from = now.minus(HISTORY_WINDOW);
            ZoneId zone = ZoneId.of(homeRepository.findById(room.getHomeId()).orElseThrow().getTimezone());
            StringBuilder history = new StringBuilder();
            history.append("Habitación: ").append(room.getName()).append('\n');
            history.append("Hora actual: ").append(ZonedDateTime.ofInstant(now, zone)).append('\n');
            history.append("Eventos de presencia (true = ocupada):\n");
            presenceRepository.findByRoomIdAndObservedAtBetween(room.getId(), from, now).stream()
                    .limit(300)
                    .forEach(e -> history.append(ZonedDateTime.ofInstant(e.getObservedAt(), zone))
                            .append(' ').append(e.isPresent()).append('\n'));
            return geminiClient.estimateReturnProbability(history.toString());
        } catch (Exception e) {
            log.warn("Gemini no respondió para la habitación {}: {}", room.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    // ---------- Ciclo de decisión ----------

    /** Casas con automatización activa. El scheduler las procesa una por una. */
    @Transactional(readOnly = true)
    public List<UUID> enabledHomeIds() {
        return settingsRepository.findByEnabledTrue().stream().map(AutomationSettingsEntity::getHomeId).toList();
    }

    /** Un ciclo por casa en su propia transacción: si una falla, las demás no se revierten. */
    @Transactional
    public int runHomeCycle(UUID homeId) {
        AutomationSettingsEntity settings = settingsRepository.findById(homeId).orElse(null);
        if (settings == null || !settings.isEnabled()) {
            return 0;
        }
        return runCycle(settings, false);
    }

    int runCycle(AutomationSettingsEntity settings, boolean manual) {
        UUID homeId = settings.getHomeId();
        Instant now = Instant.now();
        int actions = 0;
        // Apagados ya enviados que el dispositivo aún no confirma: no repetir el comando en cada ciclo.
        Set<UUID> pendingShutdown = actionRepository.findByHomeIdAndCreatedAtAfter(homeId, now.minus(PENDING_COMMAND_WINDOW))
                .stream()
                .filter(a -> a.getActionType() == ActionType.SWITCHED_OFF_VACANT_ROOM && a.getDeviceId() != null)
                .map(AutomationActionEntity::getDeviceId)
                .collect(Collectors.toSet());

        for (DeviceEntity device : deviceRepository.findByHomeIdOrderByNameAsc(homeId)) {
            if (device.getConnectionStatus() == ConnectionStatus.ONLINE
                    && device.getLastSeenAt() != null
                    && device.getLastSeenAt().isBefore(now.minus(OFFLINE_AFTER))) {
                deviceService.markOffline(device);
                alertService.raiseOnce(homeId, device.getId(), AlertType.DEVICE_OFFLINE, AlertSeverity.INFO,
                        "Dispositivo sin conexión", device.getName() + " dejó de reportar su estado", device.getRoomId());
            }
            if (device.getConnectionStatus() == ConnectionStatus.ONLINE
                    && device.getConsumptionWatts() > settings.getConsumptionAlertWatts()) {
                alertService.raiseOnce(homeId, device.getId(), AlertType.EXCESSIVE_CONSUMPTION, AlertSeverity.WARNING,
                        "Consumo excesivo", device.getName() + " consume " + Math.round(device.getConsumptionWatts())
                                + " W, por encima del umbral de " + Math.round(settings.getConsumptionAlertWatts()) + " W",
                        device.getRoomId());
            }
        }

        for (RoomEntity room : roomRepository.findByHomeIdOrderByNameAsc(homeId)) {
            Optional<PresenceEventEntity> latest = roomService.latestPresence(room.getId());
            if (latest.isEmpty() || latest.get().isPresent()) {
                continue; // sin historial o habitación ocupada: no se actúa
            }
            Instant vacantSince = latest.get().getObservedAt();
            long vacantMinutes = Duration.between(vacantSince, now).toMinutes();
            List<DeviceEntity> roomDevices = deviceRepository.findByRoomId(room.getId());

            if (vacantMinutes < settings.getVacancyMinutes()) {
                continue;
            }
            if (returnProbabilityPercent(room) >= settings.getReturnSensitivityPercent()) {
                actions += recordOnce(homeId, room, null, ActionType.KEPT_ON_LIKELY_RETURN,
                        "Se mantuvo encendida la habitación " + room.getName() + ": es probable que alguien regrese", 0);
                continue;
            }

            for (DeviceEntity device : roomDevices) {
                if (device.getPowerState() != PowerState.ON || !device.getType().switchable()
                        || pendingShutdown.contains(device.getId())) {
                    continue;
                }
                if (device.isCritical()) {
                    actions += recordOnce(homeId, room, device, ActionType.PROTECTED_CRITICAL_DEVICE,
                            device.getName() + " es crítico: se mantiene encendido en " + room.getName(), 0);
                    if (vacantMinutes >= settings.getLeakMinutes()) {
                        alertService.raiseOnce(homeId, device.getId(), AlertType.POSSIBLE_ENERGY_LEAK, AlertSeverity.CRITICAL,
                                "Posible fuga de energía", device.getName() + " lleva " + vacantMinutes
                                        + " min encendido en " + room.getName() + " sin presencia", room.getId());
                    }
                    continue;
                }
                double watts = device.getConsumptionWatts();
                if (!deviceService.commandAutomatically(homeId, device.getId(), device.getName(), DeviceService.Command.OFF)) {
                    log.warn("No se pudo apagar {} automáticamente: el broker MQTT no está disponible", device.getName());
                    continue;
                }
                actionRepository.save(newAction(homeId, room.getId(), device.getId(),
                        ActionType.SWITCHED_OFF_VACANT_ROOM,
                        "Apagó " + device.getName() + " en " + room.getName() + " (vacía hace " + vacantMinutes + " min)",
                        watts));
                alertService.raiseOnce(homeId, device.getId(), AlertType.AUTOMATIC_SHUTDOWN, AlertSeverity.INFO,
                        "Apagado automático", device.getName() + " se apagó porque " + room.getName() + " está vacía",
                        room.getId());
                actions++;
            }
        }
        if (manual && actions == 0) {
            log.info("Ciclo manual de automatización sin acciones para la casa {}", homeId);
        }
        return actions;
    }

    private int recordOnce(UUID homeId, RoomEntity room, DeviceEntity device, ActionType type, String description,
                           double watts) {
        Instant since = Instant.now().minus(Duration.ofHours(1));
        boolean exists = actionRepository.findByHomeIdAndCreatedAtAfter(homeId, since).stream()
                .anyMatch(a -> a.getActionType() == type
                        && java.util.Objects.equals(a.getRoomId(), room.getId())
                        && java.util.Objects.equals(a.getDeviceId(), device == null ? null : device.getId()));
        if (exists) {
            return 0;
        }
        actionRepository.save(newAction(homeId, room.getId(), device == null ? null : device.getId(), type, description, watts));
        return 1;
    }

    private static AutomationActionEntity newAction(UUID homeId, UUID roomId, UUID deviceId, ActionType type,
                                                    String description, double watts) {
        AutomationActionEntity action = new AutomationActionEntity();
        action.setHomeId(homeId);
        action.setRoomId(roomId);
        action.setDeviceId(deviceId);
        action.setActionType(type);
        action.setDescription(description);
        action.setWattsAvoided(watts);
        action.setCreatedAt(Instant.now());
        return action;
    }

    private static void validate(Settings s) {
        if (s.vacancyMinutes() < 1 || s.vacancyMinutes() > 1440) {
            throw ApiException.badRequest("El tiempo sin presencia debe estar entre 1 y 1440 minutos");
        }
        if (s.returnSensitivityPercent() < 1 || s.returnSensitivityPercent() > 100) {
            throw ApiException.badRequest("La sensibilidad de regreso debe estar entre 1 y 100");
        }
        if (s.consumptionAlertWatts() <= 0) {
            throw ApiException.badRequest("El umbral de consumo debe ser mayor que 0");
        }
        if (s.leakMinutes() < 1 || s.leakMinutes() > 1440) {
            throw ApiException.badRequest("El tiempo de fuga debe estar entre 1 y 1440 minutos");
        }
    }

}
