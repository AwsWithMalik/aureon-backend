package com.Accounting.app.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.OverviewPageResponse;

@RestController
public class OverviewPageController {
    private final Config config;
    private final OverviewPageServices overviewPageServices;

    public OverviewPageController(Config config, OverviewPageServices overviewPageServices) {
        this.config = config;
        this.overviewPageServices = overviewPageServices;
    }

    @GetMapping("/api/dashboard/overview")
    public ResponseEntity<OverviewPageResponse> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(overviewPageServices.overviewPageResponse(config.getEmail(), from, to));
    }
}
