package com.homeia.web;

import com.homeia.persistence.AuditLogEntity;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    public record AuditDto(Long id, String actor, String action, String entityType, String entityId,
                           String details, Instant createdAt) {
        static AuditDto from(AuditLogEntity l) {
            return new AuditDto(l.getId(), l.getActor(), l.getAction(), l.getEntityType(), l.getEntityId(),
                    l.getDetails(), l.getCreatedAt());
        }
    }

    @GetMapping
    public Map<String, Object> search(@AuthenticationPrincipal AuthPrincipal principal,
                                      @RequestParam(required = false) String action,
                                      @RequestParam(required = false) String entityType,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        Page<AuditLogEntity> result = auditService.search(principal.homeId(), action, entityType, from, to,
                page, Math.min(size, 200));
        List<AuditDto> content = result.getContent().stream().map(AuditDto::from).toList();
        return Map.of("content", content, "page", result.getNumber(), "size", result.getSize(),
                "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages());
    }
}
