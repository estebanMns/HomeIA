package com.homeia.service;

import com.homeia.domain.ConnectionStatus;
import com.homeia.domain.PowerState;
import com.homeia.integration.GeminiClient;
import com.homeia.integration.MqttGateway;
import com.homeia.integration.WhatsAppClient;
import com.homeia.persistence.AlertRepository;
import com.homeia.persistence.DeviceEntity;
import com.homeia.persistence.DeviceRepository;
import com.homeia.persistence.HomeRepository;
import com.homeia.persistence.RoomRepository;
import com.homeia.persistence.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemService {

    private final HomeRepository homeRepository;
    private final DeviceRepository deviceRepository;
    private final RoomRepository roomRepository;
    private final AlertRepository alertRepository;
    private final WhatsAppMessageRepository whatsAppMessageRepository;
    private final MqttGateway mqttGateway;
    private final WhatsAppClient whatsAppClient;
    private final GeminiClient geminiClient;

    /** Estado de los componentes. No expone secretos, solo si están configurados y conectados. */
    @Transactional(readOnly = true)
    public Map<String, Object> health() {
        boolean database = true;
        try {
            homeRepository.count();
        } catch (Exception e) {
            database = false;
        }
        return Map.of(
                "status", database ? "UP" : "DOWN",
                "database", database ? "UP" : "DOWN",
                "mqtt", mqttGateway.enabled() ? (mqttGateway.connected() ? "CONNECTED" : "DISCONNECTED") : "DISABLED",
                "whatsapp", whatsAppClient.configured() ? "CONFIGURED" : "NOT_CONFIGURED",
                "gemini", geminiClient.configured() ? "CONFIGURED" : "NOT_CONFIGURED",
                "checkedAt", Instant.now().toString());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> metrics(UUID homeId) {
        List<DeviceEntity> devices = deviceRepository.findByHomeIdOrderByNameAsc(homeId);
        long online = devices.stream().filter(d -> d.getConnectionStatus() == ConnectionStatus.ONLINE).count();
        long on = devices.stream().filter(d -> d.getPowerState() == PowerState.ON).count();
        return Map.of(
                "devicesTotal", devices.size(),
                "devicesOnline", online,
                "devicesOn", on,
                "roomsTotal", roomRepository.findByHomeIdOrderByNameAsc(homeId).size(),
                "alertsOpen", alertRepository.countByHomeIdAndAcknowledgedFalse(homeId),
                "whatsappPending", whatsAppMessageRepository
                        .findTop50ByHomeIdAndStatusOrderByCreatedAtAsc(homeId, com.homeia.domain.MessageStatus.PENDING).size(),
                "mqttConnected", mqttGateway.connected(),
                "generatedAt", Instant.now().toString());
    }
}
