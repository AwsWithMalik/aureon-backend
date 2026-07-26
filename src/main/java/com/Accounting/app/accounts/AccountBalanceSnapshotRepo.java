package com.Accounting.app.accounts;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountBalanceSnapshotRepo extends JpaRepository<AccountBalanceSnapshot, Integer> {
    Optional<AccountBalanceSnapshot> findTopByEmailAndAccountIdOrderBySnapshotAtDesc(String email, String accountId);

    Optional<AccountBalanceSnapshot> findTopByEmailAndAccountIdAndSnapshotAtBeforeOrderBySnapshotAtDesc(
            String email,
            String accountId,
            LocalDateTime snapshotAt);

    List<AccountBalanceSnapshot> findAllByEmailAndSnapshotAtBetweenOrderBySnapshotAtAsc(
            String email,
            LocalDateTime from,
            LocalDateTime to);
}
