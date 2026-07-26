package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record HoldingsPageResponse(
        String periodLabel,
        String accountLabel,
        Metrics metrics,
        List<HoldingRow> holdings,
        List<SectorPerformance> sectorPerformance,
        List<Insight> insights,
        List<AccountExposure> accountExposure) {

    public record Metrics(
            MoneyMetric totalHoldingsValue,
            OpenPositions openPositions,
            GainerLoser topGainer,
            GainerLoser topLoser,
            CashPosition cashPosition) {
    }

    public record MoneyMetric(
            BigDecimal amount,
            String currency,
            BigDecimal changePercent,
            String comparisonLabel,
            List<BigDecimal> sparkline) {
    }

    public record OpenPositions(
            int value,
            String label,
            List<BigDecimal> sparkline) {
    }

    public record GainerLoser(
            String label,
            BigDecimal amount,
            BigDecimal percent,
            String currency,
            List<BigDecimal> sparkline) {
    }

    public record CashPosition(
            BigDecimal amount,
            String currency,
            BigDecimal percentOfPortfolio,
            List<BigDecimal> sparkline) {
    }

    public record HoldingRow(
            String id,
            String symbol,
            String name,
            String accountLabel,
            String accountColor,
            String logoText,
            String logoUrl,
            BigDecimal shares,
            BigDecimal averageCost,
            BigDecimal price,
            String currency,
            BigDecimal marketValue,
            BigDecimal allocationPercent,
            BigDecimal dayChangeAmount,
            BigDecimal dayChangePercent,
            BigDecimal totalGainLossAmount,
            BigDecimal totalGainLossPercent) {
    }

    public record SectorPerformance(
            String label,
            BigDecimal value) {
    }

    public record Insight(
            String id,
            String label,
            String detail,
            String value) {
    }

    public record AccountExposure(
            String label,
            BigDecimal amount,
            String currency,
            BigDecimal percent,
            String color) {
    }
}
