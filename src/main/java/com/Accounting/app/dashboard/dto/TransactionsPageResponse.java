package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionsPageResponse(
        String periodLabel,
        String accountLabel,
        Summary summary,
        Filters filters,
        List<TransactionRow> transactions,
        Pagination pagination) {

    public record Money(BigDecimal amount, String currency) {
    }

    public record Summary(
            long totalTransactions,
            BigDecimal totalTransactionsChangePercent,
            Money totalInflow,
            BigDecimal totalInflowChangePercent,
            Money totalOutflow,
            BigDecimal totalOutflowChangePercent,
            Money netCashFlow,
            BigDecimal netCashFlowChangePercent,
            long toReviewCount) {
    }

    public record Filters(
            List<String> categories,
            List<AccountFilter> accounts,
            List<String> types,
            List<String> statuses) {
    }

    public record AccountFilter(
            String id,
            String name,
            String mask) {
    }

    public record TransactionRow(
            String id,
            String date,
            String merchant,
            String description,
            String accountId,
            String accountName,
            String accountMask,
            String category,
            String type,
            Money amount,
            String status,
            String logoText,
            String logoUrl,
            String notes,
            long attachmentCount,
            boolean includedInCashFlow,
            String transactionId,
            String plaidTransactionId) {
    }

    public record Pagination(
            int page,
            int pageSize,
            long totalItems,
            int totalPages) {
    }
}
