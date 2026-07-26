package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record FinancialHealthPageResponse(
        String periodLabel,
        String comparisonLabel,
        String currency,
        HealthScore healthScore,
        Metrics metrics,
        NetWorthBreakdown netWorthBreakdown,
        CashFlow cashFlow,
        List<Alert> alerts,
        List<SpendingComparison> spendingComparison,
        List<Goal> goals) {

    public record HealthScore(
            int score,
            int maxScore,
            String label,
            int changePoints) {
    }

    public record MetricMoney(
            BigDecimal amount,
            String currency,
            BigDecimal changePercent,
            List<SparklinePoint> sparkline) {
    }

    public record EmergencyFund(
            BigDecimal monthsCovered,
            String label) {
    }

    public record Metrics(
            MetricMoney netWorth,
            MetricMoney monthlyIncome,
            MetricMoney monthlyExpenses,
            EmergencyFund emergencyFund) {
    }

    public record SparklinePoint(BigDecimal value) {
    }

    public record NetWorthBreakdown(
            String asOfDate,
            List<BreakdownAmount> assets,
            List<BreakdownAmount> liabilities) {
    }

    public record BreakdownAmount(
            String label,
            BigDecimal amount) {
    }

    public record CashFlow(
            List<BreakdownAmount> cashIn,
            List<CashOutAmount> cashOut) {
    }

    public record CashOutAmount(
            String label,
            BigDecimal amount,
            String color) {
    }

    public record Alert(
            String id,
            String label,
            String detail,
            String severity) {
    }

    public record SpendingComparison(
            String label,
            BigDecimal amount,
            BigDecimal changePercent) {
    }

    public record Goal(
            String id,
            String label,
            BigDecimal currentAmount,
            BigDecimal targetAmount,
            int progressPercent,
            String status) {
    }
}
