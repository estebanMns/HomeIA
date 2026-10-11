package com.homeia.web;

import com.homeia.domain.MessageStatus;
import com.homeia.persistence.WhatsAppMessageEntity;
import com.homeia.persistence.WhatsAppRecipientEntity;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.WhatsAppService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppController {

    private final WhatsAppService whatsAppService;

    public WhatsAppController(WhatsAppService whatsAppService) {
        this.whatsAppService = whatsAppService;
    }

    public record RecipientDto(UUID id, String name, String phone, boolean active, Instant createdAt) {
        static RecipientDto from(WhatsAppRecipientEntity r) {
            return new RecipientDto(r.getId(), r.getName(), r.getPhoneE164(), r.isActive(), r.getCreatedAt());
        }
    }

    public record MessageDto(UUID id, UUID alertId, String recipientPhone, String body, MessageStatus status,
                             int attempts, String lastError, String providerMessageId, Instant createdAt, Instant sentAt) {
        static MessageDto from(WhatsAppMessageEntity m) {
            return new MessageDto(m.getId(), m.getAlertId(), m.getRecipientPhone(), m.getBody(), m.getStatus(),
                    m.getAttempts(), m.getLastError(), m.getProviderMessageId(), m.getCreatedAt(), m.getSentAt());
        }
    }

    public record RecipientBody(@NotBlank String name, @NotBlank String phone) {
    }

    public record UpdateRecipientBody(String name, String phone, Boolean active) {
    }

    public record TestBody(String body) {
    }

    @GetMapping("/status")
    public WhatsAppService.Status status(@AuthenticationPrincipal AuthPrincipal principal) {
        return whatsAppService.status(principal.homeId());
    }

    @GetMapping("/recipients")
    public List<RecipientDto> recipients(@AuthenticationPrincipal AuthPrincipal principal) {
        return whatsAppService.recipients(principal.homeId()).stream().map(RecipientDto::from).toList();
    }

    @PostMapping("/recipients")
    @PreAuthorize("hasRole('ADMIN')")
    public RecipientDto addRecipient(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody RecipientBody body) {
        return RecipientDto.from(whatsAppService.addRecipient(principal, body.name(), body.phone()));
    }

    @PutMapping("/recipients/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RecipientDto updateRecipient(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                        @RequestBody UpdateRecipientBody body) {
        return RecipientDto.from(whatsAppService.updateRecipient(principal, id, body.name(), body.phone(), body.active()));
    }

    @DeleteMapping("/recipients/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteRecipient(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        whatsAppService.deleteRecipient(principal, id);
    }

    @GetMapping("/messages")
    public Map<String, Object> messages(@AuthenticationPrincipal AuthPrincipal principal,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        var result = whatsAppService.messages(principal.homeId(), page, Math.min(size, 100));
        return Map.of("content", result.getContent().stream().map(MessageDto::from).toList(),
                "page", result.getNumber(), "size", result.getSize(),
                "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages());
    }

    @PostMapping("/messages/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageDto retry(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return MessageDto.from(whatsAppService.retry(principal, id));
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> test(@AuthenticationPrincipal AuthPrincipal principal, @RequestBody(required = false) TestBody body) {
        return Map.of("sentTo", whatsAppService.sendTest(principal, body == null ? null : body.body()));
    }
}
