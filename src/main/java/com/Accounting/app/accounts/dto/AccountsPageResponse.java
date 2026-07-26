package com.Accounting.app.accounts.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountsPageResponse {
    private TotalLiquidity totalLiquidity;
    private AccountMetricChanges metricChanges;
    private List<Trend> trend;
    private List<ReconciliationQueue> reconciliationQueue;
    private List<AccountsDTO> accounts;
    private List<LinkedCards> linkedCards;
}