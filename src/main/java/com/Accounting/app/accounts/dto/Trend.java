package com.Accounting.app.accounts.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trend {
    private String month;
    private BigDecimal operating;
    private BigDecimal reserve;
}
