package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseBreakdownPageResponse(
        String periodLabel,
        String accountLabel,
        String comparisonLabel,
        Metrics metrics,
        List<CategoryAmount> categoryBreakdown,
        List<CategoryAmount> expenseComposition,
        List<MonthlyTrendPeriod> monthlyTrend,
        List<Highlight> highlights,
        AiSummary aiSummary,
        List<ExpenseDetail> expenseDetails,
        List<QuickFilter> quickFilters) {

    public record Metrics(
            TotalExpenses totalExpenses,
            LargestCategory largestCategory,
            NeedsReview needsReview,
            MonthOverMonthChange monthOverMonthChange) {
    }

    public record TotalExpenses(
            BigDecimal amount,
            String currency,
            BigDecimal changePercent) {
    }

    public record LargestCategory(
            String label,
            BigDecimal amount,
            BigDecimal percentOfTotal) {
    }

    public record NeedsReview(
            long count,
            String label) {
    }

    public record MonthOverMonthChange(
            BigDecimal percent) {
    }

    public record CategoryAmount(
            String label,
            BigDecimal amount,
            BigDecimal percent,
            String color) {
    }

    public record MonthlyTrendPeriod(
            String period,
            List<TrendCategory> categories,
            BigDecimal total) {
    }

    public record TrendCategory(
            String label,
            BigDecimal amount,
            String color) {
    }

    public record Highlight(
            String id,
            String title,
            String detail,
            String tone) {
    }

    public record AiSummary(
            String summary,
            String label) {
    }

    public record ExpenseDetail(
            String id,
            String date,
            String merchant,
            String category,
            String accountName,
            BigDecimal amount,
            String currency,
            String status,
            String logoUrl,
            String logoText) {
    }

    public record QuickFilter(
            String id,
            String label,
            String icon,
            Boolean active) {
    }
}
