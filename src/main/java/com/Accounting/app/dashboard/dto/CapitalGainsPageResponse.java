package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record CapitalGainsPageResponse(
        String periodLabel,
        String accountLabel,
        String taxYearLabel,
        Metrics metrics,
        List<CumulativeGainPoint> cumulativeGains,
        List<TaxSnapshotItem> taxSnapshot,
        List<Insight> insights,
        List<ReviewItem> reviewItems,
        List<ActivityRow> activity) {

    public record Metrics(
            MoneyMetric realizedGains,
            MoneyMetric unrealizedGains,
            MoneyMetric capitalLosses,
            MoneyMetric netGain,
            MoneyMetric taxableGainEstimate) {
    }

    public record MoneyMetric(
            BigDecimal amount,
            String currency,
            BigDecimal changePercent,
            String comparisonLabel,
            List<BigDecimal> sparkline) {
    }

    public record CumulativeGainPoint(
            String period,
            BigDecimal realized,
            BigDecimal unrealized,
            BigDecimal losses) {
    }

    public record TaxSnapshotItem(
            String label,
            String value,
            String percent) {
    }

    public record Insight(
            String id,
            String label,
            String detail,
            String value,
            String tone,
            String icon) {
    }

    public record ReviewItem(
            String id,
            String label,
            String detail,
            String severity,
            String tone,
            String icon) {
    }

    public record ActivityRow(
            String id,
            String date,
            String security,
            String ticker,
            String account,
            BigDecimal proceeds,
            BigDecimal costBasis,
            BigDecimal gainLoss,
            String currency,
            String type,
            String status,
            String logoText,
            String logoUrl) {
    }
}
