package com.home.ia.infrastructure.adapter.notification;

import com.home.ia.domain.event.DomainEvent;
import com.home.ia.domain.event.EnergyLeakSuspectedEvent;
import com.home.ia.domain.event.ExcessiveConsumptionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppNotificationService {

    @Value("${whatsapp.api.url:https://api.whatsapp.com}")
    private String whatsappApiUrl;

    @Value("${whatsapp.api.token:}")
    private String whatsappToken;

    @Value("${whatsapp.phone.number:}")
    private String phoneNumber;

    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_TIME;

    public void notifyEvent(DomainEvent event) {
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
        log.info("Enviando mensaje WhatsApp a {}: {}", phoneNumber, message);
        // Aquí iría la integración real con la API de WhatsApp
        // Usar RestTemplate o WebClient para hacer POST a whatsappApiUrl
    }
}
