package com.homeia.service;

import com.homeia.persistence.AuditLogEntity;
import com.homeia.persistence.AuditLogRepository;
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
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional
    public void record(UUID homeId, String actor, String action, String entityType, String entityId, String details) {
        repository.save(new AuditLogEntity(homeId, actor, action, entityType, entityId, details));
    }

    @Transactional
    public void record(AuthPrincipal principal, String action, String entityType, String entityId, String details) {
        record(principal.homeId(), principal.email(), action, entityType, entityId, details);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogEntity> search(UUID homeId, String action, String entityType,
                                       Instant from, Instant to, int page, int size) {
        Specification<AuditLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("homeId"), homeId));
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
