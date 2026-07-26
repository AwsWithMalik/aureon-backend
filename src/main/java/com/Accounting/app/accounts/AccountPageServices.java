package com.Accounting.app.accounts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.auth.User;
import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.accounts.dto.AccountsPageResponse;
import com.Accounting.app.accounts.dto.AccountMetricChanges;
import com.Accounting.app.accounts.dto.AccountsDTO;
import com.Accounting.app.accounts.dto.Balance;
import com.Accounting.app.accounts.dto.ReconciliationQueue;
import com.Accounting.app.accounts.dto.TotalLiquidity;
import com.Accounting.app.accounts.dto.Trend;
import com.Accounting.app.accounts.dto.Limit;
import com.Accounting.app.accounts.dto.LinkedCards;
import com.Accounting.app.accounts.dto.Spend;
import com.Accounting.app.transactions.TransactionsRepo;
import com.Accounting.app.auth.UserRepo;

@Service
public class AccountPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";

    private final UserRepo userRepo;
    private final AccountRepo accountRepo;
    private final AccountBalanceSnapshotRepo accountBalanceSnapshotRepo;
    private final TransactionsRepo transactionsRepo;
    private final AccountHealthPolicy accountHealthPolicy;

    public AccountPageServices(
            UserRepo userRepo,
            AccountRepo accountRepo,
            AccountBalanceSnapshotRepo accountBalanceSnapshotRepo,
            TransactionsRepo transactionsRepo,
            AccountHealthPolicy accountHealthPolicy) {
        this.userRepo = userRepo;
        this.accountRepo = accountRepo;
        this.accountBalanceSnapshotRepo = accountBalanceSnapshotRepo;
        this.transactionsRepo = transactionsRepo;
        this.accountHealthPolicy = accountHealthPolicy;
    }

    public AccountsPageResponse accountsPageResponse(String email) {
        User user = userRepo.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found"));
        List<Account> accounts = accountRepo.findAllByUser_Id(user.getUserId());

        return new AccountsPageResponse(
                calculateTotalLiquidity(accounts),
                calculateMetricChanges(accounts),
                calculateTrend(accounts),
                calculateReconciliationQueues(accounts),
                calculateAccountsDTOs(accounts),
                calculateLinkedCards(accounts, user));
    }

    public TotalLiquidity calculateTotalLiquidity(List<Account> accounts) {
        BigDecimal total = accounts.stream()
                .map(Account::getBalance)
                .filter(balance -> balance != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TotalLiquidity(total, Currency.CAD);
    }

    public AccountMetricChanges calculateMetricChanges(List<Account> accounts) {
        BigDecimal totalCurrent = BigDecimal.ZERO;
        BigDecimal totalPrevious = BigDecimal.ZERO;
        BigDecimal cashCurrent = BigDecimal.ZERO;
        BigDecimal cashPrevious = BigDecimal.ZERO;
        BigDecimal investmentsCurrent = BigDecimal.ZERO;
        BigDecimal investmentsPrevious = BigDecimal.ZERO;
        BigDecimal creditDebtCurrent = BigDecimal.ZERO;
        BigDecimal creditDebtPrevious = BigDecimal.ZERO;

        for (Account account : accounts) {
            BigDecimal currentBalance = safe(account.getBalance());
            BigDecimal previousBalance = previousBalance(account);

            totalCurrent = totalCurrent.add(currentBalance);
            totalPrevious = totalPrevious.add(previousBalance);

            if (isInvestmentAccount(account)) {
                investmentsCurrent = investmentsCurrent.add(currentBalance);
                investmentsPrevious = investmentsPrevious.add(previousBalance);
            } else if (isCreditOrDebtAccount(account)) {
                creditDebtCurrent = creditDebtCurrent.add(currentBalance.abs());
                creditDebtPrevious = creditDebtPrevious.add(previousBalance.abs());
            } else if (isCashAccount(account)) {
                cashCurrent = cashCurrent.add(safe(account.getAvailableBalance()));
                cashPrevious = cashPrevious.add(previousAvailableBalance(account));
            }
        }

        return new AccountMetricChanges(
                percentChange(totalCurrent, totalPrevious),
                percentChange(cashCurrent, cashPrevious),
                percentChange(investmentsCurrent, investmentsPrevious),
                percentChange(creditDebtCurrent, creditDebtPrevious));
    }
    public List<Trend> calculateTrend(List<Account> accounts) {
        BigDecimal operating = BigDecimal.ZERO;
        BigDecimal reserve = BigDecimal.ZERO;

        for (Account account : accounts) {
            BigDecimal balance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;

            if (isReserveAccount(account.getAccountName(), account.getSubtype(), account.getType())) {
                reserve = reserve.add(balance);
            } else {
                operating = operating.add(balance);
            }
        }

        if (operating.compareTo(BigDecimal.ZERO) == 0 && reserve.compareTo(BigDecimal.ZERO) == 0) {
            return new ArrayList<>();
        }

        String month = LocalDate.now().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return List.of(new Trend(month, operating, reserve));
    }

    public List<ReconciliationQueue> calculateReconciliationQueues(List<Account> accounts) {
        List<ReconciliationQueue> queues = new ArrayList<>();

        for (Account account : accounts) {
            List<Transaction> transactions = transactionsRepo.findByAccountId(account.getId());
            AccountHealthPolicy.AccountHealthAssessment assessment = accountHealthPolicy.assess(account, transactions);

            queues.add(new ReconciliationQueue(
                    getStableAccountId(account),
                    fallback(account.getAccountName(), "Account"),
                    assessment.openCount(),
                    assessment.lastSyncAt(),
                    assessment.status()));
        }

        return queues;
    }

    public List<AccountsDTO> calculateAccountsDTOs(List<Account> accounts) {
        List<AccountsDTO> accountDtos = new ArrayList<>();

        for (Account account : accounts) {
            List<Transaction> transactions = transactionsRepo.findByAccountId(account.getId());
            AccountHealthPolicy.AccountHealthAssessment assessment = accountHealthPolicy.assess(account, transactions);
            BigDecimal currentBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
            BigDecimal currentAvailable = account.getAvailableBalance() != null
                    ? account.getAvailableBalance()
                    : currentBalance;
            accountDtos.add(new AccountsDTO(
                    getStableAccountId(account),
                    fallback(account.getAccountName(), "Account"),
                    institutionName(account),
                    institutionId(account),
                    institutionLogo(account),
                    institutionPrimaryColor(account),
                    institutionUrl(account),
                    fallback(account.getType(), fallback(account.getSubtype(), "account")),
                    account.getSubtype(),
                    formatMask(account.getMask()),
                    new Balance(currentBalance, DEFAULT_CURRENCY),
                    new Balance(currentAvailable, DEFAULT_CURRENCY),
                    assessment.lastSyncAt() != null ? assessment.lastSyncAt().toString() : null,
                    assessment.status(),
                    accountChangePercent(account)));
        }

        return accountDtos;
    }

    public List<LinkedCards> calculateLinkedCards(List<Account> accounts, User user) {
        List<LinkedCards> cards = new ArrayList<>();

        for (Account account : accounts) {
            if (!"credit".equalsIgnoreCase(account.getType())) {
                continue;
            }

            cards.add(new LinkedCards(
                    getStableAccountId(account),
                    fallback(account.getAccountName(), "Credit card"),
                    fallback(user.getName(), user.getEmail()),
                    new Spend(account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO, DEFAULT_CURRENCY),
                    new Limit(BigDecimal.ZERO, DEFAULT_CURRENCY)));
        }

        return cards;
    }

    private BigDecimal previousBalance(Account account) {
        BigDecimal previous = account.getPreviousBalance();
        if (previous == null && account.getLastSyncSuccessAt() != null) {
            previous = accountBalanceSnapshotRepo
                    .findTopByEmailAndAccountIdAndSnapshotAtBeforeOrderBySnapshotAtDesc(
                            account.getEmail(),
                            getStableAccountId(account),
                            account.getLastSyncSuccessAt())
                    .map(AccountBalanceSnapshot::getBalance)
                    .orElse(null);
        }
        return previous != null ? previous : BigDecimal.ZERO;
    }

    private BigDecimal previousAvailableBalance(Account account) {
        BigDecimal previous = account.getPreviousAvailableBalance();
        if (previous == null && account.getLastSyncSuccessAt() != null) {
            previous = accountBalanceSnapshotRepo
                    .findTopByEmailAndAccountIdAndSnapshotAtBeforeOrderBySnapshotAtDesc(
                            account.getEmail(),
                            getStableAccountId(account),
                            account.getLastSyncSuccessAt())
                    .map(AccountBalanceSnapshot::getAvailableBalance)
                    .orElse(null);
        }
        return previous != null ? previous : BigDecimal.ZERO;
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
    private BigDecimal accountChangePercent(Account account) {
        BigDecimal current = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        BigDecimal previous = account.getPreviousBalance();
        if (previous == null && account.getLastSyncSuccessAt() != null) {
            previous = accountBalanceSnapshotRepo
                    .findTopByEmailAndAccountIdAndSnapshotAtBeforeOrderBySnapshotAtDesc(
                            account.getEmail(),
                            getStableAccountId(account),
                            account.getLastSyncSuccessAt())
                    .map(AccountBalanceSnapshot::getBalance)
                    .orElse(null);
        }
        return percentChange(current, previous);
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null) {
            return BigDecimal.ZERO.setScale(1);
        }
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            if (current.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO.setScale(1);
            }
            return current.compareTo(BigDecimal.ZERO) > 0
                    ? BigDecimal.valueOf(100).setScale(1)
                    : BigDecimal.valueOf(-100).setScale(1);
        }
        return current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private boolean isCashAccount(Account account) {
        String value = accountValue(account);
        return value.contains("depository")
                || value.contains("checking")
                || value.contains("chequing")
                || value.contains("savings")
                || value.contains("cash");
    }

    private boolean isInvestmentAccount(Account account) {
        String value = accountValue(account);
        return value.contains("investment")
                || value.contains("brokerage")
                || value.contains("tfsa")
                || value.contains("rrsp")
                || value.contains("fhsa")
                || value.contains("securities");
    }

    private boolean isCreditOrDebtAccount(Account account) {
        String value = accountValue(account);
        return value.contains("credit")
                || value.contains("loan")
                || value.contains("mortgage")
                || value.contains("liability");
    }

    private String accountValue(Account account) {
        return String.join(" ",
                Optional.ofNullable(account.getType()).orElse(""),
                Optional.ofNullable(account.getSubtype()).orElse(""),
                Optional.ofNullable(account.getAccountName()).orElse(""),
                Optional.ofNullable(account.getOfficialName()).orElse(""))
                .toLowerCase(Locale.ROOT);
    }
    private boolean isReserveAccount(String name, String subtype, String type) {
        String value = String.join(" ",
                Optional.ofNullable(name).orElse(""),
                Optional.ofNullable(subtype).orElse(""),
                Optional.ofNullable(type).orElse(""))
                .toLowerCase(Locale.ROOT);

        return value.contains("saving") || value.contains("reserve");
    }

    private String getStableAccountId(Account account) {
        if (account.getAccountId() != null && !account.getAccountId().isBlank()) {
            return account.getAccountId();
        }

        if (account.getPlaidAccountId() != null && !account.getPlaidAccountId().isBlank()) {
            return account.getPlaidAccountId();
        }

        return account.getId() != null ? account.getId().toString() : "";
    }

    private String formatMask(String mask) {
        if (mask == null || mask.isBlank()) {
            return "****";
        }

        if (mask.startsWith("****")) {
            return mask;
        }

        return "**** " + mask;
    }

    private String institutionName(Account account) {
        PlaidItem plaidItem = account.getPlaidItem();
        return plaidItem != null ? plaidItem.getInstitutionName() : null;
    }

    private String institutionId(Account account) {
        PlaidItem plaidItem = account.getPlaidItem();
        return plaidItem != null ? plaidItem.getInstitutionId() : null;
    }

    private String institutionLogo(Account account) {
        PlaidItem plaidItem = account.getPlaidItem();
        return plaidItem != null ? plaidItem.getInstitutionLogo() : null;
    }

    private String institutionPrimaryColor(Account account) {
        PlaidItem plaidItem = account.getPlaidItem();
        return plaidItem != null ? plaidItem.getInstitutionPrimaryColor() : null;
    }

    private String institutionUrl(Account account) {
        PlaidItem plaidItem = account.getPlaidItem();
        return plaidItem != null ? plaidItem.getInstitutionUrl() : null;
    }

    private String fallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
