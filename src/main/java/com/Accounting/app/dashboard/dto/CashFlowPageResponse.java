package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowPageResponse(
        String periodLabel,
        String accountLabel,
        Metrics metrics,
        Comparisons comparisons,
        List<CashFlowPeriod> cashFlowOverTime,
        CashSnapshot cashSnapshot,
        List<CashFlowChange> changes,
        AiSummary aiSummary,
        List<CategoryAmount> topInflows,
        List<CategoryAmount> topOutflows,
        List<CashFlowTransaction> transactions) {

    public record Money(BigDecimal amount, String currency) {
    }

    public record Metrics(
            Money income,
            Money expenses,
            Money netCashFlow,
            Money transfersExcluded) {
    }

    public record Comparisons(
            BigDecimal incomePercent,
            BigDecimal expensesPercent,
            BigDecimal netCashFlowPercent,
            String comparisonLabel) {
    }

    public record CashFlowPeriod(
            String period,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal netCashFlow) {
    }

    public record CashSnapshot(
            Money startingCash,
            Money netCashFlow,
            Money endingCash,
            String startingLabel,
            String endingLabel) {
    }

    public record CashFlowChange(
            String id,
            String label,
            String detail,
            Money amount,
            String direction) {
    }

    public record AiSummary(String summary, boolean beta) {
    }

    public record CategoryAmount(String category, Money amount) {
    }

    public record CashFlowTransaction(
            String id,
            String date,
            String description,
            String type,
            String category,
            String account,
            Money amount,
            boolean included,
            String status) {
    }
}
