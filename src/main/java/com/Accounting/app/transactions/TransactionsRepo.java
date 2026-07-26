package com.Accounting.app.transactions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Accounting.app.plaid.PlaidItem;


@Repository
public interface TransactionsRepo extends JpaRepository<Transaction, Integer> {

    List<Transaction> findByAccountId(Integer accountId);

    List<Transaction> findByAccount_User_Id(Integer userId);

    List<Transaction> findByDisplayCategoryIgnoreCase(String category);

    List<Transaction> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    Optional<Transaction> findByPlaidTransactionId(String plaidTransactionId);

    Optional<Transaction> findByPlaidTransactionIdAndAccount_PlaidItem(
            String plaidTransactionId,
            PlaidItem plaidItem);

    Optional<Transaction> findByPlaidTransactionIdAndAccount_User_Id(
            String plaidTransactionId,
            Integer userId);

    Optional<Transaction> findByTransactionIdAndAccount_User_Email(Integer transactionId, String email);

    Boolean existsByPlaidTransactionId(String plaidTransactionId);

    @Query("""
            select count(t)
            from Transaction t
            where lower(t.account.email) = lower(:email)
              and t.timestamp between :from and :to
            """)
    long countByAccountEmailAndTimestampBetween(
            @Param("email") String email,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            select count(t)
            from Transaction t
            where lower(t.account.email) = lower(:email)
              and t.needsReview = true
            """)
    long countNeedsReviewByAccountEmail(@Param("email") String email);
}


// 
