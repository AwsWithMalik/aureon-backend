package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarginTrend {
    private String month;
    private BigDecimal gross;
    private BigDecimal net;
}
