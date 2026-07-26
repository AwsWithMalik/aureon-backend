package com.Accounting.app.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.CashFlowPageResponse;

@RestController
public class CashFlowPageController {
    private final Config config;
    private final CashFlowPageServices cashFlowPageServices;

    public CashFlowPageController(Config config, CashFlowPageServices cashFlowPageServices) {
        this.config = config;
        this.cashFlowPageServices = cashFlowPageServices;
    }

    @GetMapping("/api/dashboard/cash-flow")
    public ResponseEntity<CashFlowPageResponse> getCashFlow(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(cashFlowPageServices.cashFlowPageResponse(config.getEmail(), from, to));
    }
}
