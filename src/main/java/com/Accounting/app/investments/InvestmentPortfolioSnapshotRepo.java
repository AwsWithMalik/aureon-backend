package com.Accounting.app.investments;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestmentPortfolioSnapshotRepo extends JpaRepository<InvestmentPortfolioSnapshot, Integer> {
    List<InvestmentPortfolioSnapshot> findAllByEmailAndAccountIdAndSnapshotAtBetweenOrderBySnapshotAtAsc(
            String email,
            String accountId,
            LocalDateTime from,
            LocalDateTime to);

    Optional<InvestmentPortfolioSnapshot> findTopByEmailAndAccountIdAndSnapshotAtBeforeOrderBySnapshotAtDesc(
            String email,
            String accountId,
            LocalDateTime snapshotAt);

    Optional<InvestmentPortfolioSnapshot> findTopByEmailAndAccountIdOrderBySnapshotAtDesc(String email, String accountId);
}
