package com.Accounting.app.tax;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxProfileEstimatedPaymentConfig {
    private String paymentId;
    private LocalDate dueDate;
    private BigDecimal amount;
    private String currency;
    private String status;
}
