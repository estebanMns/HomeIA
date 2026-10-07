package com.home.ia.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class AlertCooldownPolicy {
    private final Duration cooldown;

    public AlertCooldownPolicy(Duration cooldown) {
        this.cooldown = Objects.requireNonNull(cooldown);
    }

    public boolean canNotify(Instant lastNotifiedAt, Instant now) {
        return lastNotifiedAt == null
                || Duration.between(lastNotifiedAt, now).compareTo(cooldown) >= 0;
    }
}
