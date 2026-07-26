package com.Accounting.app.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.ReportsPageResponse;

@RestController
public class ReportsPageController {
    private final Config config;
    private final ReportsPageServices reportsPageServices;

    public ReportsPageController(Config config, ReportsPageServices reportsPageServices) {
        this.config = config;
        this.reportsPageServices = reportsPageServices;
    }

    @GetMapping("/api/dashboard/reports")
    public ResponseEntity<ReportsPageResponse> getReports(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportsPageServices.reportsPageResponse(config.getEmail(), from, to));
    }
}
