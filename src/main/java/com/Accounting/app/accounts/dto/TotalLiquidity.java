package com.Accounting.app.accounts.dto;

import java.math.BigDecimal;

import com.Accounting.app.accounts.Currency;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TotalLiquidity {
    private BigDecimal total;
    private Currency currency;

}
