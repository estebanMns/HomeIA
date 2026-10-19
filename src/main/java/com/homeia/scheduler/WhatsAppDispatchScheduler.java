package com.homeia.scheduler;

import com.homeia.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Envía los mensajes de WhatsApp en cola. Cada mensaje va en su propia transacción, así que un fallo
 * o una respuesta lenta de Meta no afecta a los demás ni al ciclo de automatización.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppDispatchScheduler {

    private final WhatsAppService whatsAppService;

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void dispatchPending() {
        for (UUID messageId : whatsAppService.dueMessageIds(Instant.now())) {
            try {
                whatsAppService.dispatch(messageId);
            } catch (Exception e) {
                log.error("Error al despachar el mensaje de WhatsApp {}: {}", messageId, e.getMessage(), e);
            }
        }
    }
}
