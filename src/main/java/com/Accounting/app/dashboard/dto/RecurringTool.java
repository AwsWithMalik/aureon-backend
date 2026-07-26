package com.Accounting.app.dashboard.dto;

import com.Accounting.app.accounts.dto.Balance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTool {
    private String id;
    private String vendor;
    private String category;
    private Balance amount;
    private String cycle;
}
