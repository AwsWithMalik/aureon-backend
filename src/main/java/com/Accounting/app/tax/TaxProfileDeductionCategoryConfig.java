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
public class TaxProfileDeductionCategoryConfig {
    private String categoryId;
    private String name;
    private BigDecimal trackedAmount;
    private String trackedCurrency;
    private String documentationStatus;
}
