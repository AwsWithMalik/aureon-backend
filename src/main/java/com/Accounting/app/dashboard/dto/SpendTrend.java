package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpendTrend {
    private String month;
    private BigDecimal spend;
    private BigDecimal budget;
}
