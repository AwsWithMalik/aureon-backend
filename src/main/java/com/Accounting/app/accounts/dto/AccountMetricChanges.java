package com.Accounting.app.accounts.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountMetricChanges {
    private BigDecimal totalBalancePercent;
    private BigDecimal cashAvailablePercent;
    private BigDecimal investmentsPercent;
    private BigDecimal creditDebtPercent;
}