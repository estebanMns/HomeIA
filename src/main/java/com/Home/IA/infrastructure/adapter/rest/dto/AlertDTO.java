package com.home.ia.infrastructure.adapter.rest.dto;

import com.home.ia.domain.model.alert.AlertSeverity;
import com.home.ia.domain.model.alert.AlertStatus;
import com.home.ia.domain.model.alert.AlertType;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDTO {
    private String id;
    private String homeId;
    private String roomId;
    private AlertType type;
    private AlertSeverity severity;
    private AlertStatus status;
    private String message;
    private Instant createdAt;
    private Instant resolvedAt;
    private String whatsappSent;
}
