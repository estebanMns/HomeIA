package com.home.ia.infrastructure.adapter.notification;

import com.home.ia.domain.event.DomainEvent;
import com.home.ia.domain.event.EnergyLeakSuspectedEvent;
import com.home.ia.domain.event.ExcessiveConsumptionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppNotificationService {

    private final RestTemplate restTemplate;

    @Value("${whatsapp.enabled:false}")
    private boolean whatsappEnabled;

    @Value("${whatsapp.api.url:https://api.whatsapp.com}")
    private String whatsappApiUrl;

    @Value("${whatsapp.api.token:}")
    private String whatsappToken;

    @Value("${whatsapp.phone.number:}")
    private String phoneNumber;

    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_TIME;

    public void notifyEvent(DomainEvent event) {
        if (!whatsappEnabled) {
            log.debug("WhatsApp notifications are disabled");
            return;
        }

        if (whatsappToken == null || whatsappToken.isEmpty()) {
            log.warn("WhatsApp token not configured, notification not sent");
            return;
        }

        String message = buildMessage(event);
        sendWhatsAppMessage(message);
    }

    private String buildMessage(DomainEvent event) {
        return switch (event) {
            case EnergyLeakSuspectedEvent leak ->
                String.format("⚠️ *Alerta: Fuga de energía detectada*\n\n" +
                    "Dispositivo: %s\n" +
                    "Hora: %s\n" +
                    "Anomalía: %.2f%%\n\n" +
                    "Por favor, verifica el consumo anómalo.",
                    leak.deviceId().value(),
                    formatter.format(leak.occurredAt()),
                    leak.anomalyScore() * 100);

            case ExcessiveConsumptionEvent excess ->
                String.format("⚠️ *Alerta: Consumo excesivo*\n\n" +
                    "Dispositivo: %s\n" +
                    "Hora: %s\n" +
                    "Consumo: %.2f W (límite: %.2f W)\n\n" +
                    "Considera apagar dispositivos no esenciales.",
                    excess.deviceId().value(),
                    formatter.format(excess.occurredAt()),
                    excess.averageWatts(),
                    excess.thresholdWatts());

            default -> "Evento de dominio: " + event.getClass().getSimpleName();
        };
    }

    private void sendWhatsAppMessage(String message) {
        try {
            log.debug("Enviando mensaje WhatsApp a {}", phoneNumber);

            Map<String, String> payload = new HashMap<>();
            payload.put("phone", phoneNumber);
            payload.put("message", message);

            restTemplate.postForObject(
                    whatsappApiUrl + "/send",
                    payload,
                    String.class
            );

            log.info("Mensaje WhatsApp enviado exitosamente a {}", phoneNumber);
        } catch (Exception e) {
            log.error("Error enviando mensaje WhatsApp a {}: {}", phoneNumber, e.getMessage(), e);
        }
    }
}
