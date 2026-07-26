package com.Accounting.app.dashboard.dto;

public record TransactionUpdateRequest(
        String category,
        String notes,
        Boolean includedInCashFlow) {
}
