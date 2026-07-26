package com.Accounting.app.tax;

import java.math.BigDecimal;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxProfileIncomeSourceConfig {
    private String sourceId;
    private String label;
    private String category;
    private BigDecimal yearToDateAmount;
    private String yearToDateCurrency;
    private BigDecimal taxWithheldAmount;
    private String taxWithheldCurrency;
}
