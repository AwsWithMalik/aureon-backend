package com.Accounting.app.investments.dto;

import java.util.List;

import com.plaid.client.model.InvestmentAccount;
import com.plaid.client.model.Security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentTransactionsResponse {
    private List<InvestmentAccount> accounts;
    private List<Security> securities;
    private List<InvestmentTransactionDto> investmentTransactions;
    private Integer totalInvestmentTransactions;
    private String requestId;
}
