package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record BalanceSheetPageResponse(
        String asOfLabel,
        String accountLabel,
        Metrics metrics,
        StatementSide assets,
        LiabilitiesAndEquity liabilitiesAndEquity,
        List<CompositionItem> assetComposition,
        List<CompositionItem> liabilityEquityMix,
        List<Highlight> highlights,
        List<KeyRatio> keyRatios,
        AiSummary aiSummary) {

    public record Metrics(
            MoneyMetric totalAssets,
            MoneyMetric totalLiabilities,
            MoneyMetric totalEquity,
            PercentMetric debtRatio) {
    }

    public record MoneyMetric(
            BigDecimal amount,
            String currency,
            BigDecimal changePercent,
            String comparisonLabel,
            List<BigDecimal> sparkline) {
    }

    public record PercentMetric(
            BigDecimal percent,
            BigDecimal changePoints,
            String comparisonLabel,
            List<BigDecimal> sparkline) {
    }

    public record StatementSide(
            List<StatementSection> sections,
            BigDecimal totalAssets) {
    }

    public record LiabilitiesAndEquity(
            List<StatementSection> sections,
            BigDecimal totalLiabilitiesAndEquity) {
    }

    public record StatementSection(
            String label,
            BigDecimal total,
            List<StatementRow> rows) {
    }

    public record StatementRow(
            String label,
            BigDecimal amount) {
    }

    public record CompositionItem(
            String label,
            BigDecimal amount,
            BigDecimal percent,
            String color) {
    }

    public record Highlight(
            String id,
            String title,
            String detail,
            String tone) {
    }

    public record KeyRatio(
            String label,
            String value) {
    }

    public record AiSummary(
            String summary,
            String label) {
    }
}
