package com.homeia.web;

import com.homeia.security.AuthPrincipal;
import com.homeia.service.SystemService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return systemService.health();
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics(@AuthenticationPrincipal AuthPrincipal principal) {
        return systemService.metrics(principal.homeId());
    }
}
