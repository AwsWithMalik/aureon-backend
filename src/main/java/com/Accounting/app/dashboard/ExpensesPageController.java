package com.Accounting.app.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.ExpensesPageResponse;

@RestController
public class ExpensesPageController {
    private final Config config;
    private final ExpensesPageServices expensesPageServices;

    public ExpensesPageController(Config config, ExpensesPageServices expensesPageServices) {
        this.config = config;
        this.expensesPageServices = expensesPageServices;
    }

    @GetMapping("/api/dashboard/expenses")
    public ResponseEntity<ExpensesPageResponse> getExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(expensesPageServices.expensesPageResponse(config.getEmail(), from, to));
    }
}
