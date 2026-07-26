package com.Accounting.app.settings.dto;

import com.Accounting.app.accounts.dto.Balance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingSettings {
    private String planName;
    private Balance amount;
    private String interval;
    private String billingCycle;
}
