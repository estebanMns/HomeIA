package com.homeia.service;

import com.homeia.domain.AlertSeverity;
import com.homeia.domain.AlertType;
import com.homeia.persistence.AlertEntity;
import com.homeia.persistence.AlertRepository;
import com.homeia.security.AuthPrincipal;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final WhatsAppService whatsAppService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<AlertEntity> search(UUID homeId, AlertSeverity severity, Boolean acknowledged, int page, int size) {
        Specification<AlertEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("homeId"), homeId));
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (acknowledged != null) {
                predicates.add(cb.equal(root.get("acknowledged"), acknowledged));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return alertRepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public AlertEntity get(UUID homeId, UUID alertId) {
        return alertRepository.findByIdAndHomeId(alertId, homeId).orElseThrow(() -> ApiException.notFound("Alerta"));
    }

    @Transactional(readOnly = true)
    public long countOpen(UUID homeId) {
        return alertRepository.countByHomeIdAndAcknowledgedFalse(homeId);
    }

    /** Crea una alerta manual. */
    @Transactional
    public AlertEntity createManual(AuthPrincipal principal, AlertSeverity severity, String title, String message,
                                    UUID roomId, UUID deviceId) {
        if (title == null || title.isBlank() || message == null || message.isBlank()) {
            throw ApiException.badRequest("Título y mensaje son obligatorios");
        }
        return raise(principal.homeId(), AlertType.MANUAL, severity, title, message, roomId, deviceId, principal.email());
    }

    /** Crea la alerta solo si no hay una igual sin atender para el mismo dispositivo. */
    @Transactional
    public AlertEntity raiseOnce(UUID homeId, UUID deviceId, AlertType type, AlertSeverity severity,
                                 String title, String message, UUID roomId) {
        if (deviceId != null && alertRepository.existsByHomeIdAndDeviceIdAndTypeAndAcknowledgedFalse(homeId, deviceId, type)) {
            return null;
        }
        return raise(homeId, type, severity, title, message, roomId, deviceId, "SISTEMA");
    }

    private AlertEntity raise(UUID homeId, AlertType type, AlertSeverity severity, String title, String message,
                              UUID roomId, UUID deviceId, String actor) {
        AlertEntity alert = new AlertEntity();
        alert.setHomeId(homeId);
        alert.setType(type);
        alert.setSeverity(severity);
        alert.setTitle(title.trim());
        alert.setMessage(message.trim());
        alert.setRoomId(roomId);
        alert.setDeviceId(deviceId);
        alert.setCreatedAt(Instant.now());
        alertRepository.save(alert);
        auditService.record(homeId, actor, "ALERT_CREATED", "ALERT", alert.getId().toString(), title);
        if (severity != AlertSeverity.INFO) {
            whatsAppService.notifyAlert(alert);
        }
        return alert;
    }

    @Transactional
    public AlertEntity acknowledge(AuthPrincipal principal, UUID alertId) {
        AlertEntity alert = get(principal.homeId(), alertId);
        if (!alert.isAcknowledged()) {
            alert.setAcknowledged(true);
            alert.setAcknowledgedAt(Instant.now());
            alert.setAcknowledgedBy(principal.email());
            auditService.record(principal, "ALERT_ACKNOWLEDGED", "ALERT", alertId.toString(), alert.getTitle());
        }
        return alert;
    }

    /** Reenvía por WhatsApp los mensajes fallidos de la alerta. */
    @Transactional
    public int retryNotifications(AuthPrincipal principal, UUID alertId) {
        AlertEntity alert = get(principal.homeId(), alertId);
        return whatsAppService.retryFailedForAlert(principal.homeId(), alert.getId());
    }
}
