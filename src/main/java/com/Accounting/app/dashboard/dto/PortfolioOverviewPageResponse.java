package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioOverviewPageResponse(
        String periodLabel,
        String accountLabel,
        Metrics metrics,
        Performance performance,
        List<AllocationItem> assetAllocation,
        List<HoldingRow> holdings,
        DividendSnapshot dividendSnapshot,
        List<ConnectedAccount> connectedAccounts) {

    public record Metrics(
            MoneyMetric totalPortfolioValue,
            MoneyMetric totalGainLoss,
            DayChangeMetric dayChange,
            MoneyMetric dividendIncome,
            AccountsConnected accountsConnected) {
    }

    public record MoneyMetric(
            BigDecimal amount,
            String currency,
            BigDecimal changePercent,
            String comparisonLabel,
            List<BigDecimal> sparkline) {
    }

    public record DayChangeMetric(
            BigDecimal amount,
            String currency,
            BigDecimal percent,
            List<BigDecimal> sparkline) {
    }

    public record AccountsConnected(
            int value,
            String label) {
    }

    public record Performance(
            Money currentValue,
            BigDecimal totalGainLossPercent,
            BigDecimal totalGainLossAmount,
            String currency,
            List<PerformancePoint> series,
            List<String> rangeOptions,
            String selectedRange) {
    }

    public record Money(
            BigDecimal amount,
            String currency) {
    }

    public record PerformancePoint(
            String date,
            BigDecimal portfolioValue,
            BigDecimal benchmarkValue) {
    }

    public record AllocationItem(
            String label,
            BigDecimal amount,
            BigDecimal percent,
            String color) {
    }

    public record HoldingRow(
            String id,
            String symbol,
            String name,
            BigDecimal shares,
            BigDecimal price,
            String currency,
            BigDecimal marketValue,
            BigDecimal dayChangeAmount,
            BigDecimal dayChangePercent,
            BigDecimal totalGainLossAmount,
            BigDecimal totalGainLossPercent,
            String logoUrl,
            String logoText) {
    }

    public record DividendSnapshot(
            Money totalDividendIncome,
            UpcomingDividends upcomingDividends,
            TopPayer topPayer) {
    }

    public record UpcomingDividends(
            BigDecimal amount,
            String currency,
            String label) {
    }

    public record TopPayer(
            String name,
            String symbol,
            BigDecimal amount,
            String currency) {
    }

    public record ConnectedAccount(
            String id,
            String institutionName,
            String accountName,
            String accountType,
            BigDecimal amount,
            String currency,
            String status,
            String logoUrl,
            String logoText) {
    }
}
