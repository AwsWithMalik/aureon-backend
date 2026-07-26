package com.Accounting.app.investments;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Accounting.app.plaid.PlaidItem;

@Repository
public interface InvestmentTransactionRepo extends JpaRepository<InvestmentTransaction, Integer> {
    Optional<InvestmentTransaction> findByPlaidInvestmentTransactionId(String plaidInvestmentTransactionId);

    List<InvestmentTransaction> findAllByPlaidItem(PlaidItem plaidItem);
}
