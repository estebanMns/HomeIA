package com.home.ia.infrastructure.adapter.in.websocket;

import com.home.ia.domain.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onDomainEvent(DomainEvent event) {
        try {
            String notification = formatNotification(event);
            broadcastNotification("/topic/notifications", notification);
            broadcastNotification("/topic/" + event.getClass().getSimpleName(), notification);

            log.info("WebSocket notification sent for event: {}", event.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Error broadcasting WebSocket notification: {}", e.getMessage(), e);
        }
    }

    public void broadcastNotification(String destination, String message) {
        try {
            messagingTemplate.convertAndSend(destination, message);
            log.debug("Broadcast sent to {}: {}", destination, message);
        } catch (Exception e) {
            log.error("Error broadcasting to {}: {}", destination, e.getMessage());
        }
    }

    private String formatNotification(DomainEvent event) {
        return String.format("{\"event\":\"%s\",\"timestamp\":%d,\"data\":\"%s\"}",
                event.getClass().getSimpleName(),
                System.currentTimeMillis(),
                event.toString());
    }
}
