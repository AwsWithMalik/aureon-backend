package com.Accounting.app.tax.dto;

import java.time.LocalDate;

import com.Accounting.app.accounts.dto.Balance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstimatedPayment {
    private String id;
    private LocalDate dueDate;
    private Balance amount;
    private String status;
}
