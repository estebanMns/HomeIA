package com.home.ia.domain.event;

import java.time.Instant;

public interface DomainEvent {
    Instant occurredAt();
}