package com.Accounting.app.dashboard;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse;

@RestController
public class ExpenseBreakdownPageController {
    private final Config config;
    private final ExpenseBreakdownPageServices expenseBreakdownPageServices;

    public ExpenseBreakdownPageController(Config config, ExpenseBreakdownPageServices expenseBreakdownPageServices) {
        this.config = config;
        this.expenseBreakdownPageServices = expenseBreakdownPageServices;
    }

    @GetMapping("/api/dashboard/expense-breakdown")
    public ResponseEntity<ExpenseBreakdownPageResponse> getExpenseBreakdown(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(defaultValue = "all") String accountScope,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth compareTo) {
        return ResponseEntity.ok(expenseBreakdownPageServices.expenseBreakdownPageResponse(
                config.getEmail(),
                month,
                accountScope,
                compareTo));
    }
}
