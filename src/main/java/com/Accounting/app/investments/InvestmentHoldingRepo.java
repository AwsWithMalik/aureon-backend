package com.Accounting.app.investments;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Accounting.app.plaid.PlaidItem;

@Repository
public interface InvestmentHoldingRepo extends JpaRepository<InvestmentHolding, Integer> {
    Optional<InvestmentHolding> findByPlaidItemAndAccountIdAndSecurityId(
            PlaidItem plaidItem,
            String accountId,
            String securityId);

    List<InvestmentHolding> findAllByPlaidItem(PlaidItem plaidItem);
}
