package com.Accounting.app.tax.dto;

import com.Accounting.app.accounts.dto.Balance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeductionCategory {
    private String id;
    private String name;
    private Balance trackedAmount;
    private String documentationStatus;
}
