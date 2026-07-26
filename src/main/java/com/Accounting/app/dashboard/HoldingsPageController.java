package com.Accounting.app.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.HoldingsPageResponse;

@RestController
public class HoldingsPageController {
    private final Config config;
    private final HoldingsPageServices holdingsPageServices;

    public HoldingsPageController(Config config, HoldingsPageServices holdingsPageServices) {
        this.config = config;
        this.holdingsPageServices = holdingsPageServices;
    }

    @GetMapping("/api/dashboard/holdings")
    public ResponseEntity<HoldingsPageResponse> getHoldings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "all") String accountScope) {
        return ResponseEntity.ok(holdingsPageServices.holdingsPageResponse(
                config.getEmail(),
                from,
                to,
                accountScope));
    }
}
