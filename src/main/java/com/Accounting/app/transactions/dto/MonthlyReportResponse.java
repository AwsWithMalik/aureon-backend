package com.Accounting.app.transactions.dto;

import java.math.BigDecimal;

public class MonthlyReportResponse {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netCashFlow;

    public MonthlyReportResponse(BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal netCashFlow) {
        this.totalExpense = totalExpense;
        this.totalIncome = totalIncome;
        this.netCashFlow = netCashFlow;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    public BigDecimal getNetCashFlow() {
        return netCashFlow;
    }
}
