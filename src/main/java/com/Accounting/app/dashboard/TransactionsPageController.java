package com.Accounting.app.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.TransactionUpdateRequest;
import com.Accounting.app.dashboard.dto.TransactionsPageResponse;

@RestController
public class TransactionsPageController {
    private final Config config;
    private final TransactionsPageServices transactionsPageServices;

    public TransactionsPageController(Config config, TransactionsPageServices transactionsPageServices) {
        this.config = config;
        this.transactionsPageServices = transactionsPageServices;
    }

    @GetMapping("/api/dashboard/transactions")
    public ResponseEntity<TransactionsPageResponse> getTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "all") String accountScope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "false") boolean showAll) {
        return ResponseEntity.ok(transactionsPageServices.transactionsPageResponse(
                config.getEmail(),
                from,
                to,
                accountScope,
                page,
                pageSize,
                showAll));
    }

    @PatchMapping("/api/dashboard/transactions/{transactionId}")
    public ResponseEntity<TransactionsPageResponse.TransactionRow> updateTransaction(
            @PathVariable Integer transactionId,
            @RequestBody TransactionUpdateRequest request) {
        return ResponseEntity.ok(transactionsPageServices.updateTransaction(
                config.getEmail(),
                transactionId,
                request));
    }
}
