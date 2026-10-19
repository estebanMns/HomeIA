package com.homeia.service;

import com.homeia.domain.AlertSeverity;
import com.homeia.domain.MessageStatus;
import com.homeia.integration.WhatsAppClient;
import com.homeia.persistence.AlertEntity;
import com.homeia.persistence.AlertRepository;
import com.homeia.persistence.WhatsAppMessageEntity;
import com.homeia.persistence.WhatsAppMessageRepository;
import com.homeia.persistence.WhatsAppRecipientEntity;
import com.homeia.persistence.WhatsAppRecipientRepository;
import com.homeia.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private final WhatsAppRecipientRepository recipientRepository;
    private final WhatsAppMessageRepository messageRepository;
    private final WhatsAppClient client;
    private final AuditService auditService;
    private final AlertRepository alertRepository;

    public record Status(boolean configured, long activeRecipients, long pendingMessages, long failedMessages) {
    }

    // ---------- Destinatarios ----------

    @Transactional(readOnly = true)
    public List<WhatsAppRecipientEntity> recipients(UUID homeId) {
        return recipientRepository.findByHomeIdOrderByNameAsc(homeId);
    }

    @Transactional
    public WhatsAppRecipientEntity addRecipient(AuthPrincipal principal, String name, String phone) {
        String number = normalizePhone(phone);
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("El nombre del destinatario es obligatorio");
        }
        if (recipientRepository.existsByHomeIdAndPhoneE164(principal.homeId(), number)) {
            throw ApiException.conflict("Ese número ya está registrado");
        }
        WhatsAppRecipientEntity recipient = new WhatsAppRecipientEntity();
        recipient.setHomeId(principal.homeId());
        recipient.setName(name.trim());
        recipient.setPhoneE164(number);
        recipient.setActive(true);
        recipientRepository.save(recipient);
        auditService.record(principal, "WHATSAPP_RECIPIENT_ADDED", "WHATSAPP_RECIPIENT",
                recipient.getId().toString(), recipient.getName());
        return recipient;
    }

    @Transactional
    public WhatsAppRecipientEntity updateRecipient(AuthPrincipal principal, UUID recipientId, String name,
                                                   String phone, Boolean active) {
        WhatsAppRecipientEntity recipient = recipientRepository.findByIdAndHomeId(recipientId, principal.homeId())
                .orElseThrow(() -> ApiException.notFound("Destinatario"));
        if (name != null && !name.isBlank()) {
            recipient.setName(name.trim());
        }
        if (phone != null) {
            String number = normalizePhone(phone);
            if (!number.equals(recipient.getPhoneE164())
                    && recipientRepository.existsByHomeIdAndPhoneE164(principal.homeId(), number)) {
                throw ApiException.conflict("Ese número ya está registrado");
            }
            recipient.setPhoneE164(number);
        }
        if (active != null) {
            recipient.setActive(active);
        }
        auditService.record(principal, "WHATSAPP_RECIPIENT_UPDATED", "WHATSAPP_RECIPIENT",
                recipientId.toString(), recipient.getName());
        return recipient;
    }

    @Transactional
    public void deleteRecipient(AuthPrincipal principal, UUID recipientId) {
        WhatsAppRecipientEntity recipient = recipientRepository.findByIdAndHomeId(recipientId, principal.homeId())
                .orElseThrow(() -> ApiException.notFound("Destinatario"));
        recipientRepository.delete(recipient);
        auditService.record(principal, "WHATSAPP_RECIPIENT_DELETED", "WHATSAPP_RECIPIENT",
                recipientId.toString(), recipient.getName());
    }

    // ---------- Estado y mensajes ----------

    @Transactional(readOnly = true)
    public Status status(UUID homeId) {
        return new Status(
                client.configured(),
                recipientRepository.findByHomeIdAndActiveTrue(homeId).size(),
                messageRepository.countByHomeIdAndStatus(homeId, MessageStatus.PENDING),
                messageRepository.countByHomeIdAndStatus(homeId, MessageStatus.FAILED));
    }

    @Transactional(readOnly = true)
    public Page<WhatsAppMessageEntity> messages(UUID homeId, int page, int size) {
        return messageRepository.findByHomeIdOrderByCreatedAtDesc(homeId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /** Envía un mensaje de prueba a todos los destinatarios activos, ahora mismo (el usuario espera el resultado). */
    @Transactional
    public int sendTest(AuthPrincipal principal, String body) {
        String text = (body == null || body.isBlank()) ? "Prueba de notificación de HomeIA" : body.trim();
        List<WhatsAppRecipientEntity> active = recipientRepository.findByHomeIdAndActiveTrue(principal.homeId());
        if (active.isEmpty()) {
            throw ApiException.badRequest("No hay destinatarios activos");
        }
        Instant now = Instant.now();
        for (WhatsAppRecipientEntity recipient : active) {
            attempt(enqueue(principal.homeId(), null, recipient.getPhoneE164(), text, now), now);
        }
        auditService.record(principal, "WHATSAPP_TEST_SENT", "WHATSAPP", null, active.size() + " destinatarios");
        return active.size();
    }

    /** Reenvío manual de un mensaje fallido: un intento inmediato adicional. */
    @Transactional
    public WhatsAppMessageEntity retry(AuthPrincipal principal, UUID messageId) {
        WhatsAppMessageEntity message = messageRepository.findByIdAndHomeId(messageId, principal.homeId())
                .orElseThrow(() -> ApiException.notFound("Mensaje"));
        if (message.getStatus() != MessageStatus.FAILED) {
            throw ApiException.badRequest("Solo se reenvían mensajes fallidos");
        }
        return attempt(message, Instant.now());
    }

    /**
     * Encola la notificación de una alerta para todos los destinatarios activos. No envía nada aquí:
     * esto corre dentro de la transacción del ciclo de automatización y una llamada HTTP lenta la bloquearía.
     * El envío lo hace {@link #dispatch(UUID)} desde el WhatsAppDispatchScheduler.
     */
    @Transactional
    public void notifyAlert(AlertEntity alert) {
        String text = "[HomeIA · " + severityLabel(alert.getSeverity()) + "] " + alert.getTitle() + "\n" + alert.getMessage();
        Instant now = Instant.now();
        for (WhatsAppRecipientEntity recipient : recipientRepository.findByHomeIdAndActiveTrue(alert.getHomeId())) {
            enqueue(alert.getHomeId(), alert.getId(), recipient.getPhoneE164(), text, now);
        }
    }

    @Transactional
    public int retryFailedForAlert(UUID homeId, UUID alertId) {
        int retried = 0;
        Instant now = Instant.now();
        for (WhatsAppMessageEntity message : messageRepository.findTop50ByHomeIdAndStatusOrderByCreatedAtAsc(homeId, MessageStatus.FAILED)) {
            if (alertId.equals(message.getAlertId())) {
                attempt(message, now);
                retried++;
            }
        }
        return retried;
    }

    // ---------- Cola de envío ----------

    /** Ids de los mensajes pendientes cuyo siguiente intento ya venció. */
    @Transactional(readOnly = true)
    public List<UUID> dueMessageIds(Instant now) {
        return messageRepository
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(MessageStatus.PENDING, now)
                .stream()
                .map(WhatsAppMessageEntity::getId)
                .toList();
    }

    /** Un intento de envío de un mensaje de la cola, en su propia transacción. */
    @Transactional
    public void dispatch(UUID messageId) {
        messageRepository.findById(messageId)
                .filter(m -> m.getStatus() == MessageStatus.PENDING)
                .ifPresent(m -> attempt(m, Instant.now()));
    }

    private WhatsAppMessageEntity enqueue(UUID homeId, UUID alertId, String phone, String body, Instant now) {
        WhatsAppMessageEntity message = new WhatsAppMessageEntity();
        message.setHomeId(homeId);
        message.setAlertId(alertId);
        message.setRecipientPhone(phone);
        message.setBody(body);
        message.setStatus(MessageStatus.PENDING);
        message.setNextAttemptAt(now);
        return messageRepository.save(message);
    }

    /**
     * Un intento de envío. Si falla y quedan intentos, vuelve a PENDING con espera creciente (1 min, 5 min);
     * al tercer fallo queda FAILED y solo se reenvía manualmente.
     */
    private WhatsAppMessageEntity attempt(WhatsAppMessageEntity message, Instant now) {
        message.setAttempts(message.getAttempts() + 1);
        try {
            String providerId = deliver(message);
            message.setStatus(MessageStatus.SENT);
            message.setProviderMessageId(providerId);
            message.setSentAt(now);
            message.setNextAttemptAt(null);
            message.setLastError(null);
        } catch (Exception e) {
            log.warn("No se pudo enviar WhatsApp a {} (intento {}): {}", message.getRecipientPhone(),
                    message.getAttempts(), e.getMessage());
            message.setLastError(e.getMessage());
            if (WhatsAppDeliveryPolicy.hasAttemptsLeft(message.getAttempts())) {
                message.setStatus(MessageStatus.PENDING);
                message.setNextAttemptAt(WhatsAppDeliveryPolicy.nextAttemptAt(message.getAttempts(), now));
            } else {
                message.setStatus(MessageStatus.FAILED);
                message.setNextAttemptAt(null);
            }
        }
        return messageRepository.save(message);
    }

    /** Con plantilla configurada usa la plantilla de Meta ({{1}} severidad, {{2}} título, {{3}} detalle). */
    private String deliver(WhatsAppMessageEntity message) {
        if (!client.usesTemplate()) {
            return client.sendText(message.getRecipientPhone(), message.getBody());
        }
        AlertEntity alert = message.getAlertId() == null ? null
                : alertRepository.findById(message.getAlertId()).orElse(null);
        List<String> params = alert == null
                ? List.of("PRUEBA", "Notificación de HomeIA", WhatsAppDeliveryPolicy.templateParam(message.getBody()))
                : List.of(severityLabel(alert.getSeverity()),
                        WhatsAppDeliveryPolicy.templateParam(alert.getTitle()),
                        WhatsAppDeliveryPolicy.templateParam(alert.getMessage()));
        return client.sendAlertTemplate(message.getRecipientPhone(), params);
    }

    static String normalizePhone(String phone) {
        if (phone == null) {
            throw ApiException.badRequest("El número es obligatorio");
        }
        String cleaned = phone.replaceAll("[\\s\\-()]", "");
        if (!E164.matcher(cleaned).matches()) {
            throw ApiException.badRequest("El número debe estar en formato internacional, p. ej. +573001234567");
        }
        return cleaned;
    }

    private static String severityLabel(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "CRÍTICA";
            case WARNING -> "ADVERTENCIA";
            case INFO -> "INFO";
        };
    }
}
