package com.Accounting.app.plaid;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface PlaidItemRepo extends JpaRepository<PlaidItem, Integer> {

    Optional<PlaidItem> findByUserId(Integer userId);

    List<PlaidItem> findAllByUser_Email(String email);

    Optional<PlaidItem> findByPlaidItemId(String plaidItemId);
}
