package com.Accounting.app.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.CapitalGainsPageResponse;

@RestController
public class CapitalGainsPageController {
    private final Config config;
    private final CapitalGainsPageServices capitalGainsPageServices;

    public CapitalGainsPageController(
            Config config,
            CapitalGainsPageServices capitalGainsPageServices) {
        this.config = config;
        this.capitalGainsPageServices = capitalGainsPageServices;
    }

    @GetMapping("/api/dashboard/capital-gains")
    public ResponseEntity<CapitalGainsPageResponse> getCapitalGains(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "all") String accountScope) {
        return ResponseEntity.ok(capitalGainsPageServices.capitalGainsPageResponse(
                config.getEmail(),
                from,
                to,
                accountScope));
    }
}
