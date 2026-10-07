package com.home.ia.infrastructure.adapter.rest.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutomationResultDTO {
    private int sensorsEvaluated;
    private List<String> switchedOffDeviceIds;
    private Instant executedAt;
}
