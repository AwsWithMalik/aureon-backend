package com.Accounting.app.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.InvestmentTransactionsPageResponse;

@RestController
public class InvestmentTransactionsPageController {
    private final Config config;
    private final InvestmentTransactionsPageServices investmentTransactionsPageServices;

    public InvestmentTransactionsPageController(
            Config config,
            InvestmentTransactionsPageServices investmentTransactionsPageServices) {
        this.config = config;
        this.investmentTransactionsPageServices = investmentTransactionsPageServices;
    }

    @GetMapping("/api/dashboard/investment-transactions")
    public ResponseEntity<InvestmentTransactionsPageResponse> getInvestmentTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "all") String accountScope) {
        return ResponseEntity.ok(investmentTransactionsPageServices.investmentTransactionsPageResponse(
                config.getEmail(),
                from,
                to,
                accountScope));
    }
}
