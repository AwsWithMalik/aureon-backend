package com.Accounting.app.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.DividendsPageResponse;

@RestController
public class DividendsPageController {
    private final Config config;
    private final DividendsPageServices dividendsPageServices;

    public DividendsPageController(Config config, DividendsPageServices dividendsPageServices) {
        this.config = config;
        this.dividendsPageServices = dividendsPageServices;
    }

    @GetMapping("/api/dashboard/dividends")
    public ResponseEntity<DividendsPageResponse> getDividends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "all") String accountScope) {
        return ResponseEntity.ok(dividendsPageServices.dividendsPageResponse(
                config.getEmail(),
                from,
                to,
                accountScope));
    }
}
