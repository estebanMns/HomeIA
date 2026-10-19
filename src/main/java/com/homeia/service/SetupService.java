package com.homeia.service;

import com.homeia.config.HomeIaProperties;
import com.homeia.domain.UserRole;
import com.homeia.persistence.AutomationSettingsEntity;
import com.homeia.persistence.AutomationSettingsRepository;
import com.homeia.persistence.HomeEntity;
import com.homeia.persistence.HomeRepository;
import com.homeia.persistence.UserEntity;
import com.homeia.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Alta de casas y administradores.
 * - {@link #setup}: primera configuración de la aplicación. Solo funciona mientras no exista ninguna casa,
 *   así no quedan credenciales por defecto en el código.
 * - {@link #register}: registro público. Cada cuenta nueva crea su propia casa con los valores por defecto.
 */
@Service
@RequiredArgsConstructor
public class SetupService {

    private final HomeRepository homeRepository;
    private final UserRepository userRepository;
    private final AutomationSettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final HomeIaProperties properties;

    public record SetupRequest(String homeName, String currency, BigDecimal electricityPricePerKwh, String timezone,
                               String adminFullName, String adminEmail, String adminPassword,
                               int vacancyMinutes, int returnSensitivityPercent,
                               double consumptionAlertWatts, int leakMinutes) {
    }

    @Transactional(readOnly = true)
    public boolean needsSetup() {
        return homeRepository.count() == 0;
    }

    @Transactional
    public synchronized HomeEntity setup(SetupRequest request) {
        if (!needsSetup()) {
            throw ApiException.conflict("La aplicación ya fue configurada");
        }
        return createHomeWithAdmin(request);
    }

    /** Registro público: crea una casa nueva con el usuario como administrador, usando los valores por defecto. */
    @Transactional
    public synchronized HomeEntity register(String fullName, String email, String password) {
        HomeIaProperties.Defaults d = properties.defaults();
        return createHomeWithAdmin(new SetupRequest(
                d.homeName(), d.currency(), d.electricityPricePerKwh(), d.timezone(),
                fullName, email, password,
                d.vacancyMinutes(), d.returnSensitivityPercent(), d.consumptionAlertWatts(), d.leakMinutes()));
    }

    private HomeEntity createHomeWithAdmin(SetupRequest request) {
        require(request.homeName(), "El nombre de la casa es obligatorio");
        require(request.currency(), "La moneda es obligatoria");
        require(request.timezone(), "La zona horaria es obligatoria");
        require(request.adminFullName(), "El nombre del administrador es obligatorio");
        if (request.electricityPricePerKwh() == null || request.electricityPricePerKwh().signum() < 0) {
            throw ApiException.badRequest("La tarifa de energía es obligatoria y no puede ser negativa");
        }
        HomeService.validateZone(request.timezone());
        String email = UserService.normalizeEmail(request.adminEmail());
        PasswordPolicy.validate(request.adminPassword(), request.adminFullName(), email);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Ya existe una cuenta con ese correo");
        }

        HomeEntity home = new HomeEntity();
        home.setName(request.homeName().trim());
        home.setCurrency(request.currency().trim().toUpperCase());
        home.setElectricityPricePerKwh(request.electricityPricePerKwh());
        home.setTimezone(request.timezone().trim());
        homeRepository.save(home);

        UserEntity admin = new UserEntity();
        admin.setHomeId(home.getId());
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
        admin.setFullName(request.adminFullName().trim());
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);

        settingsRepository.save(new AutomationSettingsEntity(home.getId(), false,
                request.vacancyMinutes(), request.returnSensitivityPercent(),
                request.consumptionAlertWatts(), request.leakMinutes()));

        auditService.record(home.getId(), email, "SETUP_COMPLETED", "HOME", home.getId().toString(), home.getName());
        return home;
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
    }
}
