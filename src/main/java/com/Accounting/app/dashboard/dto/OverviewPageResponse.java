package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record OverviewPageResponse(
        String periodLabel,
        String comparisonLabel,
        Money totalBalance,
        Money netProfit,
        CashFlow cashFlow,
        Comparisons comparisons,
        List<MonthlyRevenuePoint> monthlyRevenue,
        List<SparklinePoint> profitSparkline,
        List<ExpenseBreakdownItem> expenseBreakdown,
        List<RecentTransactionItem> recentTransactions,
        List<InvoiceSummaryItem> invoiceSummary,
        List<ReviewQueueItem> reviewQueue,
        AiSummary aiSummary) {

    public record Money(
            BigDecimal amount,
            String currency) {
    }

    public record CashFlow(
            Money inflow,
            Money outflow) {
    }

    public record Comparisons(
            BigDecimal totalBalancePercent,
            BigDecimal netCashFlowPercent,
            BigDecimal incomePercent,
            BigDecimal expensesPercent) {
    }

    public record MonthlyRevenuePoint(
            String month,
            BigDecimal value) {
    }

    public record SparklinePoint(
            BigDecimal value) {
    }

    public record ExpenseBreakdownItem(
            String name,
            BigDecimal value,
            String color) {
    }

    public record RecentTransactionItem(
            String id,
            String merchant,
            String category,
            BigDecimal amount,
            String status,
            String occurredAt) {
    }

    public record InvoiceSummaryItem(
            String label,
            int value) {
    }

    public record ReviewQueueItem(
            String id,
            String label,
            String detail,
            String date,
            String priority,
            String tone,
            String icon,
            String actionLabel,
            String route) {
    }

    public record AiSummary(
            String summary,
            List<String> points) {
    }
}
