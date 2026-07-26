package com.Accounting.app.investments.dto;

import java.util.List;

import com.plaid.client.model.Holding;
import com.plaid.client.model.InvestmentAccount;
import com.plaid.client.model.Security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentHoldingsResponse {
    private List<InvestmentAccount> accounts;
    private List<Holding> holdings;
    private List<Security> securities;
    private String requestId;
}
