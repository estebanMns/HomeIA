package com.home.ia.application.port.out;

import com.home.ia.domain.event.DomainEvent;

public interface DomainEventPublisherPort {
    void publish(DomainEvent event);
}