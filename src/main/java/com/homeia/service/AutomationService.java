package com.homeia.service;

import com.homeia.domain.ActionType;
import com.homeia.domain.AlertSeverity;
import com.homeia.domain.AlertType;
import com.homeia.domain.ConnectionStatus;
import com.homeia.domain.PowerState;
import com.homeia.integration.GeminiClient;
import com.homeia.integration.Json;
import com.homeia.persistence.AutomationActionEntity;
import com.homeia.persistence.AutomationActionRepository;
import com.homeia.persistence.AutomationSettingsEntity;
import com.homeia.persistence.AutomationSettingsRepository;
import com.homeia.persistence.DeviceEntity;
import com.homeia.persistence.DeviceRepository;
import com.homeia.persistence.EnergyReadingEntity;
import com.homeia.persistence.EnergyReadingRepository;
import com.homeia.persistence.HomeEntity;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Motor de automatización por casa.
 *
 * Con Gemini configurado, la IA decide cada ciclo qué dispositivos encender, apagar o dejar como están
 * ({@link AiEnergyPlanner}). Cada decisión pasa por guardarraíles en código: nunca se apaga un dispositivo
 * crítico ni uno en una habitación ocupada, y nunca se enciende uno que ya está encendido.
 * Si la IA no responde, se usan las reglas de respaldo:
 * 1. Si la habitación lleva "vacancyMinutes" sin presencia, apaga luces y tomacorrientes no críticos,
 *    salvo que la probabilidad de regreso supere la sensibilidad configurada.
 * 2. Si un dispositivo crítico sigue encendido en una habitación vacía durante "leakMinutes", alerta de posible fuga.
 *
 * En cualquier caso, el motor calcula el consumo de la última hora y su costo, y alerta si un dispositivo
 * supera "consumptionAlertWatts" (las alertas de advertencia se envían por WhatsApp).
 * Si un dispositivo deja de reportar, alerta de dispositivo sin conexión.
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
    private final EnergyReadingRepository energyRepository;
    private final DeviceService deviceService;
    private final AlertService alertService;
    private final RoomService roomService;
    private final BillingService billingService;
    private final AiEnergyPlanner aiPlanner;
    private final GeminiClient geminiClient;
    private final AuditService auditService;
    private final com.homeia.persistence.HomeRepository homeRepository;

    private final Map<UUID, Map.Entry<Instant, Double>> predictionCache = new HashMap<>();

    /** Estado de una habitación según su último evento de presencia. known=false si nunca hubo eventos. */
    private record RoomState(boolean known, boolean occupied, long vacantMinutes) {
    }

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
        HomeEntity home = homeRepository.findById(homeId).orElseThrow();
        ZoneId zone = ZoneId.of(home.getTimezone());

        List<DeviceEntity> devices = deviceRepository.findByHomeIdOrderByNameAsc(homeId);
        Map<UUID, RoomEntity> rooms = new LinkedHashMap<>();
        roomRepository.findByHomeIdOrderByNameAsc(homeId).forEach(r -> rooms.put(r.getId(), r));
        Map<UUID, RoomState> roomStates = roomStates(rooms.values(), now);
        Map<UUID, Double> kwhLastHour = kwhLastHour(homeId, now);
        // Comandos enviados que el dispositivo aún no confirma: no repetirlos en cada ciclo.
        Set<UUID> pendingCommand = actionRepository.findByHomeIdAndCreatedAtAfter(homeId, now.minus(PENDING_COMMAND_WINDOW))
                .stream()
                .filter(a -> (a.getActionType() == ActionType.SWITCHED_OFF_VACANT_ROOM
                        || a.getActionType() == ActionType.SWITCHED_ON_OCCUPIED_ROOM) && a.getDeviceId() != null)
                .map(AutomationActionEntity::getDeviceId)
                .collect(Collectors.toSet());

        checkDevices(settings, home, zone, devices, kwhLastHour, now);

        int actions = 0;
        if (aiPlanner.enabled()) {
            if (!manual && !aiPlanner.due(homeId, now)) {
                return actions; // la IA decidió hace poco: se espera al siguiente intervalo
            }
            String snapshot = snapshot(settings, home, zone, devices, rooms, roomStates, kwhLastHour, now);
            Set<UUID> knownIds = devices.stream().map(DeviceEntity::getId).collect(Collectors.toSet());
            Optional<List<AiEnergyPlanner.Decision>> plan = aiPlanner.plan(homeId, snapshot, knownIds);
            if (plan.isPresent()) {
                actions += applyAiDecisions(homeId, devices, rooms, roomStates, plan.get(), pendingCommand);
                return actions;
            }
            log.warn("La IA no respondió para la casa {}: se usan las reglas de respaldo", homeId);
        }

        actions += applyRules(settings, homeId, devices, rooms, roomStates, pendingCommand, now);
        if (manual && actions == 0) {
            log.info("Ciclo manual de automatización sin acciones para la casa {}", homeId);
        }
        return actions;
    }

    /** Dispositivos sin conexión y consumo excesivo, con su consumo de la última hora y costo estimado. */
    private void checkDevices(AutomationSettingsEntity settings, HomeEntity home, ZoneId zone,
                              List<DeviceEntity> devices, Map<UUID, Double> kwhLastHour, Instant now) {
        UUID homeId = settings.getHomeId();
        for (DeviceEntity device : devices) {
            if (device.getConnectionStatus() == ConnectionStatus.ONLINE
                    && device.getLastSeenAt() != null
                    && device.getLastSeenAt().isBefore(now.minus(OFFLINE_AFTER))) {
                deviceService.markOffline(device);
                alertService.raiseOnce(homeId, device.getId(), AlertType.DEVICE_OFFLINE, AlertSeverity.INFO,
                        "Dispositivo sin conexión", device.getName() + " dejó de reportar su estado", device.getRoomId());
            }
            if (device.getConnectionStatus() == ConnectionStatus.ONLINE
                    && device.getConsumptionWatts() > settings.getConsumptionAlertWatts()) {
                double kwh = kwhLastHour.getOrDefault(device.getId(), 0.0);
                BigDecimal cost = billingService.energyCost(homeId, BigDecimal.valueOf(kwh), now,
                        home.getElectricityPricePerKwh(), zone).setScale(0, RoundingMode.HALF_UP);
                alertService.raiseOnce(homeId, device.getId(), AlertType.EXCESSIVE_CONSUMPTION, AlertSeverity.WARNING,
                        "Consumo excesivo", device.getName() + " consume " + Math.round(device.getConsumptionWatts())
                                + " W, por encima del umbral de " + Math.round(settings.getConsumptionAlertWatts())
                                + " W. En la última hora: " + String.format("%.3f", kwh) + " kWh, costo estimado "
                                + cost + " " + home.getCurrency(),
                        device.getRoomId());
            }
        }
    }

    /** Aplica el plan de la IA, validando cada decisión contra el estado real. Devuelve las acciones ejecutadas. */
    private int applyAiDecisions(UUID homeId, List<DeviceEntity> devices, Map<UUID, RoomEntity> rooms,
                                 Map<UUID, RoomState> roomStates, List<AiEnergyPlanner.Decision> decisions,
                                 Set<UUID> pendingCommand) {
        Map<UUID, DeviceEntity> byId = devices.stream().collect(Collectors.toMap(DeviceEntity::getId, d -> d));
        int actions = 0;
        for (AiEnergyPlanner.Decision decision : decisions) {
            DeviceEntity device = byId.get(decision.deviceId());
            if (device == null || !device.getType().switchable() || pendingCommand.contains(device.getId())) {
                continue;
            }
            RoomEntity room = device.getRoomId() == null ? null : rooms.get(device.getRoomId());
            RoomState state = room == null ? null : roomStates.get(room.getId());
            if (state == null || !state.known()) {
                continue; // sin datos de presencia no hay base para decidir
            }

            if ("OFF".equals(decision.action())) {
                if (device.getPowerState() != PowerState.ON || device.isCritical() || state.occupied()) {
                    continue; // guardarraíles: críticos y habitaciones ocupadas nunca se apagan
                }
                if (!deviceService.commandAutomatically(homeId, device.getId(), device.getName(), DeviceService.Command.OFF)) {
                    log.warn("No se pudo apagar {} automáticamente: el broker MQTT no está disponible", device.getName());
                    continue;
                }
                actionRepository.save(newAction(homeId, room.getId(), device.getId(), ActionType.SWITCHED_OFF_VACANT_ROOM,
                        "IA apagó " + device.getName() + " en " + room.getName() + " (vacía hace " + state.vacantMinutes()
                                + " min). Motivo: " + decision.reason(), device.getConsumptionWatts()));
                alertService.raiseOnce(homeId, device.getId(), AlertType.AUTOMATIC_SHUTDOWN, AlertSeverity.INFO,
                        "Apagado automático", device.getName() + " se apagó porque " + room.getName() + " está vacía",
                        room.getId());
                actions++;
            } else if ("ON".equals(decision.action())) {
                if (device.getPowerState() != PowerState.OFF || !state.occupied()) {
                    continue; // guardarraíles: solo se enciende lo apagado en una habitación con presencia
                }
                if (!deviceService.commandAutomatically(homeId, device.getId(), device.getName(), DeviceService.Command.ON)) {
                    log.warn("No se pudo encender {} automáticamente: el broker MQTT no está disponible", device.getName());
                    continue;
                }
                actionRepository.save(newAction(homeId, room.getId(), device.getId(), ActionType.SWITCHED_ON_OCCUPIED_ROOM,
                        "IA encendió " + device.getName() + " en " + room.getName() + " (hay presencia). Motivo: "
                                + decision.reason(), 0));
                actions++;
            }
        }
        return actions;
    }

    /** Reglas de respaldo, usadas cuando la IA no está configurada o no respondió. */
    private int applyRules(AutomationSettingsEntity settings, UUID homeId, List<DeviceEntity> devices,
                           Map<UUID, RoomEntity> rooms, Map<UUID, RoomState> roomStates, Set<UUID> pendingCommand,
                           Instant now) {
        int actions = 0;
        for (RoomEntity room : rooms.values()) {
            RoomState state = roomStates.get(room.getId());
            if (state == null || !state.known() || state.occupied()) {
                continue; // sin historial o habitación ocupada: no se actúa
            }
            long vacantMinutes = state.vacantMinutes();
            if (vacantMinutes < settings.getVacancyMinutes()) {
                continue;
            }
            if (returnProbabilityPercent(room) >= settings.getReturnSensitivityPercent()) {
                actions += recordOnce(homeId, room, null, ActionType.KEPT_ON_LIKELY_RETURN,
                        "Se mantuvo encendida la habitación " + room.getName() + ": es probable que alguien regrese", 0);
                continue;
            }

            for (DeviceEntity device : devices) {
                if (!room.getId().equals(device.getRoomId())) {
                    continue;
                }
                if (device.getPowerState() != PowerState.ON || !device.getType().switchable()
                        || pendingCommand.contains(device.getId())) {
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
        return actions;
    }

    /** Estado de presencia de cada habitación según su último evento. */
    private Map<UUID, RoomState> roomStates(Collection<RoomEntity> rooms, Instant now) {
        Map<UUID, RoomState> states = new HashMap<>();
        for (RoomEntity room : rooms) {
            Optional<PresenceEventEntity> latest = roomService.latestPresence(room.getId());
            if (latest.isEmpty()) {
                states.put(room.getId(), new RoomState(false, false, 0));
                continue;
            }
            boolean occupied = latest.get().isPresent();
            long vacantMinutes = occupied ? 0 : Duration.between(latest.get().getObservedAt(), now).toMinutes();
            states.put(room.getId(), new RoomState(true, occupied, vacantMinutes));
        }
        return states;
    }

    /** kWh consumidos en la última hora por dispositivo, integrando las lecturas de potencia (regla del trapecio). */
    private Map<UUID, Double> kwhLastHour(UUID homeId, Instant now) {
        Map<UUID, Double> kwh = new HashMap<>();
        EnergyReadingEntity previous = null;
        for (EnergyReadingEntity reading : energyRepository
                .findByHomeIdAndMeasuredAtBetweenOrderByDeviceIdAscMeasuredAtAsc(homeId, now.minus(Duration.ofHours(1)), now)) {
            if (previous != null && previous.getDeviceId().equals(reading.getDeviceId())) {
                double hours = Duration.between(previous.getMeasuredAt(), reading.getMeasuredAt()).toMillis() / 3_600_000.0;
                double avgWatts = (previous.getWatts() + reading.getWatts()) / 2.0;
                kwh.merge(reading.getDeviceId(), avgWatts / 1000.0 * hours, Double::sum);
            }
            previous = reading;
        }
        return kwh;
    }

    /** Estado de la casa en JSON para que la IA decida. Sin datos sensibles: solo lo necesario para decidir. */
    private String snapshot(AutomationSettingsEntity settings, HomeEntity home, ZoneId zone, List<DeviceEntity> devices,
                            Map<UUID, RoomEntity> rooms, Map<UUID, RoomState> roomStates, Map<UUID, Double> kwhLastHour,
                            Instant now) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (DeviceEntity device : devices) {
            RoomEntity room = device.getRoomId() == null ? null : rooms.get(device.getRoomId());
            RoomState state = room == null ? null : roomStates.get(room.getId());
            boolean known = state != null && state.known();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deviceId", device.getId().toString());
            item.put("nombre", device.getName());
            item.put("tipo", device.getType().name());
            item.put("critico", device.isCritical());
            item.put("encendido", device.getPowerState() == PowerState.ON);
            item.put("conectado", device.getConnectionStatus() == ConnectionStatus.ONLINE);
            item.put("vatios", Math.round(device.getConsumptionWatts()));
            item.put("kwh_ultima_hora", Math.round(kwhLastHour.getOrDefault(device.getId(), 0.0) * 1000) / 1000.0);
            item.put("habitacion", room == null ? "sin habitación" : room.getName());
            item.put("habitacion_ocupada", known ? state.occupied() : null);
            item.put("minutos_sin_presencia", known && !state.occupied() ? state.vacantMinutes() : null);
            items.add(item);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hora_local", ZonedDateTime.ofInstant(now, zone).toString());
        root.put("moneda", home.getCurrency());
        root.put("precio_kwh", home.getElectricityPricePerKwh());
        root.put("minutos_minimos_para_considerar_vacia", settings.getVacancyMinutes());
        root.put("umbral_consumo_vatios", settings.getConsumptionAlertWatts());
        root.put("dispositivos", items);
        return Json.write(root);
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
