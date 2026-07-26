package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvestmentTransactionsPageResponse(
        String periodLabel,
        String accountLabel,
        Metrics metrics,
        List<ActivityPoint> activity,
        Breakdown breakdown,
        List<Insight> insights,
        List<TransactionRow> transactions) {

    public record Metrics(
            MoneyMetric totalBuys,
            MoneyMetric totalSells,
            MoneyMetric dividendIncome,
            MoneyMetric feesAndTaxes,
            MoneyMetric netActivity) {
    }

    public record MoneyMetric(
            BigDecimal amount,
            String currency,
            BigDecimal changePercent,
            String comparisonLabel,
            List<BigDecimal> sparkline) {
    }

    public record ActivityPoint(
            String period,
            BigDecimal buys,
            BigDecimal sells,
            BigDecimal dividends,
            BigDecimal fees,
            BigDecimal net) {
    }

    public record Breakdown(
            int totalTransactions,
            List<BreakdownItem> items) {
    }

    public record BreakdownItem(
            String label,
            BigDecimal percent,
            long count,
            String color) {
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

    public record TransactionRow(
            String id,
            String date,
            String security,
            String ticker,
            String account,
            String type,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal amount,
            String currency,
            String status,
            String logoText,
            String logoUrl) {
    }
}
