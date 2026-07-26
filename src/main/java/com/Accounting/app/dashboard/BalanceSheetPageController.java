package com.Accounting.app.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse;

@RestController
public class BalanceSheetPageController {
    private final Config config;
    private final BalanceSheetPageServices balanceSheetPageServices;

    public BalanceSheetPageController(Config config, BalanceSheetPageServices balanceSheetPageServices) {
        this.config = config;
        this.balanceSheetPageServices = balanceSheetPageServices;
    }

    @GetMapping("/api/dashboard/balance-sheet")
    public ResponseEntity<BalanceSheetPageResponse> getBalanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(defaultValue = "all") String accountScope) {
        return ResponseEntity.ok(balanceSheetPageServices.balanceSheetPageResponse(
                config.getEmail(),
                asOf,
                accountScope));
    }
}
