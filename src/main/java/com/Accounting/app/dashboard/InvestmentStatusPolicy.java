package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.Accounting.app.investments.InvestmentHolding;
import com.Accounting.app.investments.InvestmentTransaction;
import com.Accounting.app.plaid.PlaidItem;

@Component
public class InvestmentStatusPolicy {
    private static final int SYNCING_WINDOW_MINUTES = 15;
    private static final int STALE_SYNC_DAYS = 7;

    public String determineConnectedAccountStatus(PlaidItem plaidItem, List<InvestmentHolding> holdings) {
        if (plaidItem == null || blank(plaidItem.getAccessToken()) || holdings == null || holdings.isEmpty()) {
            return "needs_attention";
        }

        String persistedStatus = persistedInvestmentSyncStatus(plaidItem);
        if (persistedStatus != null) {
            return persistedStatus;
        }

        LocalDateTime latestSync = holdings.stream()
                .map(InvestmentHolding::getSyncedAt)
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        if (latestSync == null) {
            return "needs_attention";
        }

        boolean incompleteHoldings = holdings.stream()
                .anyMatch(this::hasIncompleteHoldingState);
        if (latestSync.isAfter(LocalDateTime.now().minusMinutes(SYNCING_WINDOW_MINUTES)) && incompleteHoldings) {
            return "syncing";
        }

        if (latestSync.isBefore(LocalDateTime.now().minusDays(STALE_SYNC_DAYS)) || incompleteHoldings) {
            return "needs_attention";
        }

        return "synced";
    }

    private String persistedInvestmentSyncStatus(PlaidItem plaidItem) {
        LocalDateTime attemptAt = plaidItem.getLastInvestmentSyncAttemptAt();
        LocalDateTime successAt = plaidItem.getLastInvestmentSyncSuccessAt();
        LocalDateTime failureAt = plaidItem.getLastInvestmentSyncFailureAt();

        if (attemptAt == null && successAt == null && failureAt == null) {
            return null;
        }

        if (attemptAt != null) {
            boolean successResolvedAttempt = successAt != null && !successAt.isBefore(attemptAt);
            boolean failureResolvedAttempt = failureAt != null && !failureAt.isBefore(attemptAt);
            if (!successResolvedAttempt && !failureResolvedAttempt) {
                return "syncing";
            }
        }

        if (failureAt != null && (successAt == null || failureAt.isAfter(successAt))) {
            return "needs_attention";
        }

        if (successAt != null) {
            return "synced";
        }

        return "needs_attention";
    }

    public String determineInvestmentTransactionStatus(InvestmentTransaction transaction) {
        if (transaction == null) {
            return "needs_attention";
        }
        if (transaction.getDate() != null && transaction.getDate().isAfter(LocalDate.now())) {
            return "pending";
        }
        if (missingRequiredHistoryFields(transaction)) {
            return "needs_attention";
        }
        return "posted";
    }

    private boolean hasIncompleteHoldingState(InvestmentHolding holding) {
        return holding == null
                || blank(holding.getAccountId())
                || blank(holding.getAccountName())
                || blank(holding.getSecurityId())
                || blank(holding.getSecurityName())
                || safe(holding.getInstitutionValue()).compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean missingRequiredHistoryFields(InvestmentTransaction transaction) {
        return transaction.getDate() == null
                || safe(transaction.getAmount()).compareTo(BigDecimal.ZERO) == 0
                || blank(transaction.getAccountId())
                || blank(transaction.getAccountName())
                || blank(transaction.getSecurityName());
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
