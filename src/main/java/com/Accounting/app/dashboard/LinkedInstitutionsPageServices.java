package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.accounts.Account;
import com.Accounting.app.accounts.AccountRepo;
import com.Accounting.app.dashboard.dto.LinkedInstitutionsPageResponse;
import com.Accounting.app.dashboard.dto.LinkedInstitutionsPageResponse.InstitutionSummary;
import com.Accounting.app.dashboard.dto.LinkedInstitutionsPageResponse.LinkedAccount;
import com.Accounting.app.dashboard.dto.LinkedInstitutionsPageResponse.Metric;
import com.Accounting.app.dashboard.dto.LinkedInstitutionsPageResponse.Metrics;
import com.Accounting.app.dashboard.dto.LinkedInstitutionsPageResponse.ProductEnabled;
import com.Accounting.app.dashboard.dto.LinkedInstitutionsPageResponse.SelectedInstitution;
import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.plaid.PlaidItemRepo;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.transactions.TransactionsRepo;

@Service
public class LinkedInstitutionsPageServices {
    private static final String CHANGE_LABEL = "current baseline";

    private final PlaidItemRepo plaidItemRepo;
    private final AccountRepo accountRepo;
    private final TransactionsRepo transactionsRepo;

    public LinkedInstitutionsPageServices(
            PlaidItemRepo plaidItemRepo,
            AccountRepo accountRepo,
            TransactionsRepo transactionsRepo) {
        this.plaidItemRepo = plaidItemRepo;
        this.accountRepo = accountRepo;
        this.transactionsRepo = transactionsRepo;
    }

    @Transactional(readOnly = true)
    public LinkedInstitutionsPageResponse linkedInstitutionsPageResponse(String email) {
        List<PlaidItem> plaidItems = plaidItemRepo.findAllByUser_Email(email);
        List<Account> userAccounts = accountRepo.findAllByEmail(email);

        List<InstitutionSummary> institutions = plaidItems.stream()
                .map(plaidItem -> institutionSummary(plaidItem, accountsForItem(userAccounts, plaidItem)))
                .sorted(Comparator.comparing(InstitutionSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        SelectedInstitution selectedInstitution = plaidItems.stream()
                .findFirst()
                .map(plaidItem -> selectedInstitution(plaidItem, accountsForItem(userAccounts, plaidItem)))
                .orElse(null);

        return new LinkedInstitutionsPageResponse(
                "Last 30 days",
                metrics(institutions, userAccounts.size()),
                institutions,
                selectedInstitution);
    }

    private Metrics metrics(List<InstitutionSummary> institutions, int linkedAccountCount) {
        int connectedInstitutions = institutions.size();
        int healthyConnections = (int) institutions.stream()
                .filter(this::isHealthy)
                .count();
        int needsAttention = (int) institutions.stream()
                .filter(this::needsAttention)
                .count();
        int successRate = calculateSuccessRate(healthyConnections, connectedInstitutions);

        return new Metrics(
                metric(connectedInstitutions),
                metric(healthyConnections),
                metric(needsAttention),
                metric(linkedAccountCount),
                metric(successRate));
    }

    private Metric metric(int value) {
        return new Metric(value, 0, CHANGE_LABEL);
    }

    private InstitutionSummary institutionSummary(PlaidItem plaidItem, List<Account> accounts) {
        InstitutionAssessment assessment = assessInstitution(plaidItem, accounts);

        return new InstitutionSummary(
                stableInstitutionId(plaidItem),
                institutionName(plaidItem),
                assessment.type(),
                plaidItem.getInstitutionLogo(),
                logoText(institutionName(plaidItem)),
                accounts.size(),
                assessment.productsEnabled(),
                assessment.lastSyncedAt(),
                assessment.syncStatus());
    }

    private SelectedInstitution selectedInstitution(PlaidItem plaidItem, List<Account> accounts) {
        InstitutionAssessment assessment = assessInstitution(plaidItem, accounts);

        return new SelectedInstitution(
                stableInstitutionId(plaidItem),
                institutionName(plaidItem),
                assessment.type(),
                plaidItem.getInstitutionLogo(),
                logoText(institutionName(plaidItem)),
                assessment.syncStatus(),
                accounts.stream().map(this::linkedAccount).toList(),
                assessment.productsEnabled(),
                assessment.lastSyncedAt(),
                assessment.connectionStatus(),
                assessment.permissionSummary());
    }

    private LinkedAccount linkedAccount(Account account) {
        return new LinkedAccount(
                stableAccountId(account),
                fallback(account.getAccountName(), fallback(account.getOfficialName(), "Account")),
                cleanMask(account.getMask()),
                fallback(account.getType(), "account"),
                account.getSubtype());
    }

    private List<ProductEnabled> determineProductsEnabled(List<Account> accounts) {
        boolean hasAccounts = !accounts.isEmpty();
        boolean hasInvestment = accounts.stream().anyMatch(this::isInvestmentAccount);
        boolean hasLiability = accounts.stream().anyMatch(this::isLiabilityAccount);

        return List.of(
                new ProductEnabled("transactions", "Transactions", hasTransactionsEnabled(accounts)),
                new ProductEnabled("investments", "Investments", hasInvestment),
                new ProductEnabled("liabilities", "Liabilities", hasLiability),
                new ProductEnabled("identity", "Identity", false),
                new ProductEnabled("assets", "Assets", hasAccounts));
    }

    private InstitutionAssessment assessInstitution(PlaidItem plaidItem, List<Account> accounts) {
        List<ProductEnabled> productsEnabled = determineProductsEnabled(accounts);
        String syncStatus = determineSyncStatus(plaidItem, accounts);
        return new InstitutionAssessment(
                institutionType(accounts),
                syncStatus,
                determineConnectionStatus(syncStatus),
                determinePermissionSummary(productsEnabled),
                determineLastSyncedAt(plaidItem, accounts),
                productsEnabled);
    }

    private List<Account> accountsForItem(List<Account> accounts, PlaidItem plaidItem) {
        Integer itemId = plaidItem.getItemId();
        return accounts.stream()
                .filter(account -> {
                    PlaidItem accountItem = account.getPlaidItem();
                    return accountItem != null
                            && itemId != null
                            && itemId.equals(accountItem.getItemId());
                })
                .toList();
    }

    private String determineSyncStatus(PlaidItem plaidItem, List<Account> accounts) {
        if (needsReconnect(plaidItem)) {
            return "needs_reconnect";
        }

        if (isSyncing(plaidItem, accounts)) {
            return "syncing";
        }

        if (hasLimitedAccess(accounts)) {
            return "limited_access";
        }

        return "healthy";
    }

    private boolean needsReconnect(PlaidItem plaidItem) {
        return !hasText(plaidItem.getAccessToken());
    }

    private boolean isSyncing(PlaidItem plaidItem, List<Account> accounts) {
        if (!hasText(plaidItem.getAccessToken()) || accounts.isEmpty()) {
            return false;
        }

        LocalDateTime newestAccountTimestamp = accounts.stream()
                .map(account -> account.getDateAdded() != null ? account.getDateAdded() : account.getCreatedAt())
                .filter(timestamp -> timestamp != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (newestAccountTimestamp == null) {
            return false;
        }

        boolean hasNoTransactionsYet = accounts.stream()
                .noneMatch(account -> !transactionsRepo.findByAccountId(account.getId()).isEmpty());

        return hasNoTransactionsYet && newestAccountTimestamp.isAfter(LocalDateTime.now().minusMinutes(15));
    }

    private boolean hasLimitedAccess(List<Account> accounts) {
        return accounts.isEmpty();
    }

    private boolean hasTransactionsEnabled(List<Account> accounts) {
        return accounts.stream()
                .anyMatch(account -> !isInvestmentAccount(account) && !isLiabilityAccount(account));
    }

    private String determineConnectionStatus(String syncStatus) {
        return switch (syncStatus) {
            case "needs_reconnect" -> "Needs reconnect";
            case "limited_access" -> "Limited access";
            case "syncing" -> "Syncing";
            default -> "Healthy";
        };
    }

    private String determinePermissionSummary(List<ProductEnabled> productsEnabled) {
        List<String> enabledProducts = productsEnabled.stream()
                .filter(ProductEnabled::enabled)
                .map(ProductEnabled::label)
                .toList();

        if (enabledProducts.isEmpty()) {
            return "No active data permissions are available for this institution.";
        }

        return "Read-only access to your " + String.join(", ", enabledProducts).toLowerCase(Locale.US) + " data.";
    }

    private boolean isHealthy(InstitutionSummary institution) {
        return "healthy".equals(institution.syncStatus());
    }

    private boolean needsAttention(InstitutionSummary institution) {
        return !"healthy".equals(institution.syncStatus());
    }

    private int calculateSuccessRate(int healthyConnections, int connectedInstitutions) {
        return connectedInstitutions == 0
                ? 0
                : BigDecimal.valueOf(healthyConnections)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(connectedInstitutions), 0, RoundingMode.HALF_UP)
                        .intValue();
    }

    private String institutionType(List<Account> accounts) {
        if (accounts.stream().anyMatch(this::isInvestmentAccount)) {
            return "brokerage";
        }

        if (!accounts.isEmpty() && accounts.stream().allMatch(this::isCreditAccount)) {
            return "credit";
        }

        return "bank";
    }

    private boolean isInvestmentAccount(Account account) {
        String value = accountValue(account);
        return value.contains("investment")
                || value.contains("brokerage")
                || value.contains("securities")
                || value.contains("tfsa")
                || value.contains("rrsp")
                || value.contains("ira");
    }

    private boolean isLiabilityAccount(Account account) {
        String value = accountValue(account);
        return value.contains("credit")
                || value.contains("loan")
                || value.contains("mortgage")
                || value.contains("line of credit");
    }

    private boolean isCreditAccount(Account account) {
        return accountValue(account).contains("credit");
    }

    private String accountValue(Account account) {
        return String.join(" ",
                fallback(account.getType(), ""),
                fallback(account.getSubtype(), ""),
                fallback(account.getAccountName(), ""),
                fallback(account.getOfficialName(), ""))
                .toLowerCase(Locale.US);
    }

    private String determineLastSyncedAt(PlaidItem plaidItem, List<Account> accounts) {
        LocalDateTime lastSyncedAt = firstNonNull(
                plaidItem.getLastAccountSyncSuccessAt(),
                newestAccountSuccessAt(accounts),
                newestTransactionTimestamp(accounts),
                newestAccountCreatedAt(accounts));

        return lastSyncedAt == null ? null : lastSyncedAt.toString();
    }

    private LocalDateTime newestAccountSuccessAt(List<Account> accounts) {
        return accounts.stream()
                .map(Account::getLastSyncSuccessAt)
                .filter(timestamp -> timestamp != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalDateTime newestTransactionTimestamp(List<Account> accounts) {
        return accounts.stream()
                .flatMap(account -> transactionsRepo.findByAccountId(account.getId()).stream())
                .map(Transaction::getTimestamp)
                .filter(timestamp -> timestamp != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalDateTime newestAccountCreatedAt(List<Account> accounts) {
        return accounts.stream()
                .map(account -> account.getDateAdded() != null ? account.getDateAdded() : account.getCreatedAt())
                .filter(timestamp -> timestamp != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalDateTime firstNonNull(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String stableInstitutionId(PlaidItem plaidItem) {
        if (hasText(plaidItem.getInstitutionId())) {
            return plaidItem.getInstitutionId();
        }

        if (hasText(plaidItem.getPlaidItemId())) {
            return plaidItem.getPlaidItemId();
        }

        return plaidItem.getItemId() == null ? "" : "plaid-item-" + plaidItem.getItemId();
    }

    private String stableAccountId(Account account) {
        if (hasText(account.getAccountId())) {
            return account.getAccountId();
        }

        if (hasText(account.getPlaidAccountId())) {
            return account.getPlaidAccountId();
        }

        return account.getId() == null ? "" : "account-" + account.getId();
    }

    private String institutionName(PlaidItem plaidItem) {
        return fallback(plaidItem.getInstitutionName(), "Linked institution");
    }

    private String logoText(String value) {
        if (!hasText(value)) {
            return "LI";
        }

        String[] words = value.trim().split("\\s+");
        if (words.length >= 2) {
            return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(Locale.US);
        }

        String cleaned = value.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isBlank()) {
            return "LI";
        }

        return cleaned.length() <= 2
                ? cleaned.toUpperCase(Locale.US)
                : cleaned.substring(0, 2).toUpperCase(Locale.US);
    }

    private String cleanMask(String mask) {
        if (!hasText(mask)) {
            return null;
        }

        return mask.replace("*", "").trim();
    }

    private String fallback(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record InstitutionAssessment(
            String type,
            String syncStatus,
            String connectionStatus,
            String permissionSummary,
            String lastSyncedAt,
            List<ProductEnabled> productsEnabled) {
    }
}


