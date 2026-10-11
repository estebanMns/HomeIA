package com.homeia.web;

import com.homeia.persistence.HomeEntity;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.HomeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    public record HomeDto(UUID id, String name, String currency, BigDecimal electricityPricePerKwh,
                          String timezone, Instant updatedAt) {
        static HomeDto from(HomeEntity home) {
            return new HomeDto(home.getId(), home.getName(), home.getCurrency(), home.getElectricityPricePerKwh(),
                    home.getTimezone(), home.getUpdatedAt());
        }
    }

    public record UpdateHomeBody(String name, String currency, BigDecimal electricityPricePerKwh, String timezone) {
    }

    @GetMapping
    public HomeDto get(@AuthenticationPrincipal AuthPrincipal principal) {
        return HomeDto.from(homeService.get(principal.homeId()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public HomeDto update(@AuthenticationPrincipal AuthPrincipal principal, @RequestBody UpdateHomeBody body) {
        return HomeDto.from(homeService.update(principal, body.name(), body.currency(),
                body.electricityPricePerKwh(), body.timezone()));
    }
}
