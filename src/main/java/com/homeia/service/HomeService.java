package com.homeia.service;

import com.homeia.persistence.HomeEntity;
import com.homeia.persistence.HomeRepository;
import com.homeia.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final HomeRepository homeRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public HomeEntity get(UUID homeId) {
        return homeRepository.findById(homeId).orElseThrow(() -> ApiException.notFound("Casa"));
    }

    @Transactional
    public HomeEntity update(AuthPrincipal principal, String name, String currency,
                             BigDecimal electricityPricePerKwh, String timezone) {
        HomeEntity home = get(principal.homeId());
        if (name != null && !name.isBlank()) {
            home.setName(name.trim());
        }
        if (currency != null && !currency.isBlank()) {
            home.setCurrency(currency.trim().toUpperCase());
        }
        if (electricityPricePerKwh != null) {
            if (electricityPricePerKwh.signum() < 0) {
                throw ApiException.badRequest("La tarifa no puede ser negativa");
            }
            home.setElectricityPricePerKwh(electricityPricePerKwh);
        }
        if (timezone != null && !timezone.isBlank()) {
            validateZone(timezone);
            home.setTimezone(timezone.trim());
        }
        home.setUpdatedAt(Instant.now());
        auditService.record(principal, "HOME_UPDATED", "HOME", home.getId().toString(), home.getName());
        return home;
    }

    static void validateZone(String zone) {
        try {
            ZoneId.of(zone);
        } catch (Exception e) {
            throw ApiException.badRequest("Zona horaria inválida: " + zone);
        }
    }
}
