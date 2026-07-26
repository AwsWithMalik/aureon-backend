package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record DividendsPageResponse(
        String periodLabel,
        String accountLabel,
        Metrics metrics,
        List<IncomeTrendPoint> incomeTrend,
        List<UpcomingCalendarItem> upcomingCalendar,
        Breakdown breakdown,
        List<HistoryItem> history,
        List<Insight> insights,
        List<YieldSnapshotItem> yieldSnapshot) {

    public record Metrics(
            MoneyMetric totalDividendIncome,
            MoneyMetric ytdDividends,
            UpcomingPayouts upcomingPayouts,
            AverageDividendYield averageDividendYield,
            HighestDividendPayer highestDividendPayer) {
    }

    public record MoneyMetric(
            BigDecimal amount,
            String currency,
            BigDecimal changePercent,
            String comparisonLabel,
            List<BigDecimal> sparkline) {
    }

    public record UpcomingPayouts(
            BigDecimal amount,
            String currency,
            String label,
            List<BigDecimal> sparkline) {
    }

    public record AverageDividendYield(
            BigDecimal percent,
            BigDecimal changePoints,
            String comparisonLabel,
            List<BigDecimal> sparkline) {
    }

    public record HighestDividendPayer(
            String label,
            BigDecimal amount,
            String currency,
            String detailLabel,
            List<BigDecimal> sparkline) {
    }

    public record IncomeTrendPoint(
            String period,
            BigDecimal income,
            BigDecimal movingAverage) {
    }

    public record UpcomingCalendarItem(
            String id,
            String date,
            String security,
            String account,
            BigDecimal estimatedAmount,
            String currency,
            String status,
            String logoText,
            String logoUrl) {
    }

    public record Breakdown(
            BreakdownTotal total,
            List<BreakdownItem> items) {
    }

    public record BreakdownTotal(
            BigDecimal amount,
            String currency,
            String label) {
    }

    public record BreakdownItem(
            String label,
            BigDecimal percent,
            BigDecimal amount,
            String currency,
            String color) {
    }

    public record HistoryItem(
            String id,
            String date,
            String security,
            String account,
            String type,
            BigDecimal dividendPerShare,
            BigDecimal shares,
            BigDecimal totalAmount,
            String currency,
            BigDecimal yieldPercent,
            String status,
            String logoText,
            String logoUrl) {
    }

    public record Insight(
            String id,
            String label,
            String detail,
            String value,
            String sub,
            String tone,
            String icon) {
    }

    public record YieldSnapshotItem(
            String label,
            BigDecimal value,
            BigDecimal percent,
            String color) {
    }
}
