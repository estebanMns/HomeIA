package com.home.ia.infrastructure.adapter.event;

import com.home.ia.application.port.out.DomainEventPublisherPort;
import com.home.ia.domain.event.DomainEvent;
import com.home.ia.domain.event.EnergyLeakSuspectedEvent;
import com.home.ia.domain.event.ExcessiveConsumptionEvent;
import com.home.ia.infrastructure.adapter.notification.WhatsAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisherAdapter implements DomainEventPublisherPort {

    private final ApplicationEventPublisher eventPublisher;
    private final WhatsAppNotificationService whatsappService;

    @Override
    public void publish(DomainEvent event) {
        log.info("Publishing domain event: {}", event.getClass().getSimpleName());

        eventPublisher.publishEvent(event);

        if (shouldNotify(event)) {
            whatsappService.notifyEvent(event);
        }
    }

    private boolean shouldNotify(DomainEvent event) {
        return event instanceof EnergyLeakSuspectedEvent ||
               event instanceof ExcessiveConsumptionEvent;
    }
}
