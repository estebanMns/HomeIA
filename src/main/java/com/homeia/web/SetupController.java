package com.homeia.web;

import com.homeia.config.HomeIaProperties;
import com.homeia.persistence.HomeEntity;
import com.homeia.service.SetupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final SetupService setupService;
    private final HomeIaProperties.Defaults defaults;

    public SetupController(SetupService setupService, HomeIaProperties properties) {
        this.setupService = setupService;
        this.defaults = properties.defaults();
    }

    /** Solo lo esencial para crear la cuenta. */
    public record SetupBody(
            @NotBlank String adminFullName,
            @NotBlank String adminEmail,
            @NotBlank String adminPassword) {
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("needsSetup", setupService.needsSetup());
    }

    @PostMapping
    public Map<String, Object> setup(@Valid @RequestBody SetupBody body) {
        HomeEntity home = setupService.setup(new SetupService.SetupRequest(
                defaults.homeName(), defaults.currency(), defaults.electricityPricePerKwh(), defaults.timezone(),
                body.adminFullName(), body.adminEmail(), body.adminPassword(),
                defaults.vacancyMinutes(), defaults.returnSensitivityPercent(),
                defaults.consumptionAlertWatts(), defaults.leakMinutes()));
        return Map.of("homeId", home.getId().toString(), "name", home.getName());
    }
}
