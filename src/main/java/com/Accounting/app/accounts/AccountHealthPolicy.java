package com.Accounting.app.accounts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.transactions.Transaction;

@Component
public class AccountHealthPolicy {
    private static final int STALE_SYNC_DAYS = 7;
    private static final int HIGH_PENDING_COUNT = 5;

    public AccountHealthAssessment assess(Account account, List<Transaction> transactions) {
        int openCount = transactions == null ? 0 : (int) transactions.stream()
                .filter(Transaction::isPending)
                .count();
        LocalDateTime lastSyncAt = resolveLastSyncAt(account, transactions);
        String status = determineStatus(account, openCount, lastSyncAt);
        return new AccountHealthAssessment(openCount, lastSyncAt, status);
    }

    private String determineStatus(Account account, int openCount, LocalDateTime lastSyncAt) {
        if (account == null || missingIdentity(account)) {
            return "attention";
        }
        if (account.getLastSyncFailureAt() != null
                && (account.getLastSyncSuccessAt() == null
                        || account.getLastSyncFailureAt().isAfter(account.getLastSyncSuccessAt()))) {
            return "attention";
        }
        PlaidItem plaidItem = account.getPlaidItem();
        if (plaidItem != null && plaidItem.getLastAccountSyncFailureAt() != null
                && (plaidItem.getLastAccountSyncSuccessAt() == null
                        || plaidItem.getLastAccountSyncFailureAt().isAfter(plaidItem.getLastAccountSyncSuccessAt()))) {
            return "attention";
        }
        if (missingBalances(account)) {
            return "attention";
        }
        if (lastSyncAt == null || lastSyncAt.isBefore(LocalDateTime.now().minusDays(STALE_SYNC_DAYS))) {
            return "attention";
        }
        if (openCount >= HIGH_PENDING_COUNT) {
            return "attention";
        }
        return "healthy";
    }

    private LocalDateTime resolveLastSyncAt(Account account, List<Transaction> transactions) {
        if (account != null && account.getLastSyncSuccessAt() != null) {
            return account.getLastSyncSuccessAt();
        }
        if (account != null && account.getPlaidItem() != null
                && account.getPlaidItem().getLastAccountSyncSuccessAt() != null) {
            return account.getPlaidItem().getLastAccountSyncSuccessAt();
        }
        LocalDateTime transactionSyncAt = transactions == null ? null : transactions.stream()
                .map(Transaction::getTimestamp)
                .filter(timestamp -> timestamp != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (transactionSyncAt != null) {
            return transactionSyncAt;
        }
        if (account == null) {
            return null;
        }
        if (account.getDateAdded() != null) {
            return account.getDateAdded();
        }
        return account.getCreatedAt();
    }

    private boolean missingIdentity(Account account) {
        return blank(account.getAccountId())
                && blank(account.getPlaidAccountId());
    }

    private boolean missingBalances(Account account) {
        BigDecimal balance = account.getBalance();
        BigDecimal availableBalance = account.getAvailableBalance();
        return balance == null && availableBalance == null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record AccountHealthAssessment(
            int openCount,
            LocalDateTime lastSyncAt,
            String status) {
    }
}
