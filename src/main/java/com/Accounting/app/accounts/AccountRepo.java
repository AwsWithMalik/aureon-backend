package com.Accounting.app.accounts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Accounting.app.plaid.PlaidItem;


@Repository
public interface AccountRepo extends JpaRepository<Account, Integer> {

    Optional<Account> findByAccountId(String accountId);

    Optional<Account> findByAccountIdAndPlaidItem(String accountId, PlaidItem plaidItem);

    Optional<Account> findByPlaidAccountId(String plaidAccountId);

    Optional<Account> findByPlaidAccountIdAndPlaidItem(String plaidAccountId, PlaidItem plaidItem);

    Optional<Account> findByPlaidAccountIdAndUser_Id(String plaidAccountId, Integer userId);

    Optional<Account> findByEmail(String email);

    List<Account> findAllByEmail(String email);

    List<Account> findAllByUser_Id(Integer userId);

    Boolean existsByPlaidAccountId(String plaidAccountId);
}
