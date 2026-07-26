package com.Accounting.app.dashboard;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse;

@RestController
public class FinancialHealthPageController {
    private final Config config;
    private final FinancialHealthPageServices financialHealthPageServices;

    public FinancialHealthPageController(
            Config config,
            FinancialHealthPageServices financialHealthPageServices) {
        this.config = config;
        this.financialHealthPageServices = financialHealthPageServices;
    }

    @GetMapping("/api/dashboard/financial-health")
    public ResponseEntity<FinancialHealthPageResponse> getFinancialHealth() {
        return ResponseEntity.ok(financialHealthPageServices.financialHealthPageResponse(config.getEmail()));
    }
}
