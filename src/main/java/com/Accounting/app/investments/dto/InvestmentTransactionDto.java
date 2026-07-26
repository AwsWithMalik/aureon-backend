package com.Accounting.app.investments.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentTransactionDto {
    private String id;
    private String account;
    private String securityName;
    private String ticker;
    private String type;
    private BigDecimal amount;
    private BigDecimal quantity;
    private BigDecimal price;
    private LocalDate date;
    private String currency;
}
