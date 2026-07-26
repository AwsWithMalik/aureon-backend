package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.accounts.Account;
import com.Accounting.app.accounts.AccountBalanceSnapshot;
import com.Accounting.app.accounts.AccountBalanceSnapshotRepo;
import com.Accounting.app.accounts.AccountRepo;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse.AiSummary;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse.CompositionItem;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse.Highlight;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse.KeyRatio;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse.LiabilitiesAndEquity;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse.Metrics;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse.MoneyMetric;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse.PercentMetric;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse.StatementRow;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse.StatementSection;
import com.Accounting.app.dashboard.dto.BalanceSheetPageResponse.StatementSide;
import com.Accounting.app.investments.InvestmentHolding;
import com.Accounting.app.investments.InvestmentHoldingRepo;
import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.plaid.PlaidItemRepo;
import com.Accounting.app.transactions.TransactionType;
import com.Accounting.app.transactions.TransactionsRepo;

@Service
public class BalanceSheetPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final String[] ASSET_COLORS = { "#2563eb", "#0ea5e9", "#10b981", "#64748b" };
    private static final String[] LIABILITY_COLORS = { "#f97316", "#a16207", "#6366f1" };

    private final AccountRepo accountRepo;
    private final AccountBalanceSnapshotRepo accountBalanceSnapshotRepo;
    private final TransactionsRepo transactionsRepo;
    private final PlaidItemRepo plaidItemRepo;
    private final InvestmentHoldingRepo investmentHoldingRepo;

    public BalanceSheetPageServices(
            AccountRepo accountRepo,
            AccountBalanceSnapshotRepo accountBalanceSnapshotRepo,
            TransactionsRepo transactionsRepo,
            PlaidItemRepo plaidItemRepo,
            InvestmentHoldingRepo investmentHoldingRepo) {
        this.accountRepo = accountRepo;
        this.accountBalanceSnapshotRepo = accountBalanceSnapshotRepo;
        this.transactionsRepo = transactionsRepo;
        this.plaidItemRepo = plaidItemRepo;
        this.investmentHoldingRepo = investmentHoldingRepo;
    }

    @Transactional(readOnly = true)
    public BalanceSheetPageResponse balanceSheetPageResponse(String email, LocalDate asOf, String accountScope) {
        LocalDate normalizedAsOf = asOf == null ? LocalDate.now() : asOf;
        LocalDate comparisonDate = YearMonth.from(normalizedAsOf).minusMonths(1).atEndOfMonth();
        List<Account> accounts = scopedAccounts(accountRepo.findAllByEmail(email), accountScope);
        List<PlaidItem> plaidItems = plaidItemRepo.findAllByUser_Email(email);
        boolean useSnapshotBalances = hasSnapshotHistory(accounts);

        BigDecimal cash = cashAssets(accounts, normalizedAsOf, useSnapshotBalances);
        BigDecimal savings = savingsAssets(accounts, normalizedAsOf, useSnapshotBalances);
        BigDecimal investments = investmentAssets(plaidItems, accounts, normalizedAsOf, useSnapshotBalances);
        BigDecimal otherAssets = otherAssets(accounts, normalizedAsOf, useSnapshotBalances);
        BigDecimal currentAssets = cash.add(savings);
        BigDecimal longTermAssets = investments.add(otherAssets);
        BigDecimal totalAssets = currentAssets.add(longTermAssets);

        BigDecimal creditCards = creditLiabilities(accounts, normalizedAsOf, useSnapshotBalances);
        BigDecimal shortTermLoans = shortTermLoanLiabilities(accounts, normalizedAsOf, useSnapshotBalances);
        BigDecimal longTermLoans = longTermLoanLiabilities(accounts, normalizedAsOf, useSnapshotBalances);
        BigDecimal otherLiabilities = otherLiabilities(accounts, normalizedAsOf, useSnapshotBalances);
        BigDecimal currentLiabilities = creditCards.add(shortTermLoans);
        BigDecimal longTermLiabilities = longTermLoans.add(otherLiabilities);
        BigDecimal totalLiabilities = currentLiabilities.add(longTermLiabilities);
        BigDecimal totalEquity = totalAssets.subtract(totalLiabilities);
        BigDecimal liabilitiesAndEquityTotal = totalLiabilities.add(totalEquity);

        BigDecimal previousAssets = previousAssetTotal(accounts, totalAssets, comparisonDate, normalizedAsOf, useSnapshotBalances);
        BigDecimal previousLiabilities = previousLiabilityTotal(accounts, totalLiabilities, comparisonDate, useSnapshotBalances);
        BigDecimal previousEquity = previousAssets.subtract(previousLiabilities);
        BigDecimal debtRatio = percent(totalLiabilities, totalAssets);
        BigDecimal previousDebtRatio = percent(previousLiabilities, previousAssets);
        String comparisonLabel = "vs " + comparisonDate.format(LABEL_DATE);

        StatementSide assetSide = new StatementSide(
                List.of(
                        new StatementSection("Current Assets", currentAssets, List.of(
                                new StatementRow("Cash and Cash Equivalents", cash),
                                new StatementRow("Savings", savings))),
                        new StatementSection("Long-Term Assets", longTermAssets, List.of(
                                new StatementRow("Investments", investments),
                                new StatementRow("Other Assets", otherAssets)))),
                totalAssets);

        LiabilitiesAndEquity liabilitiesAndEquity = new LiabilitiesAndEquity(
                List.of(
                        new StatementSection("Current Liabilities", currentLiabilities, List.of(
                                new StatementRow("Credit Cards", creditCards),
                                new StatementRow("Short-Term Loans", shortTermLoans))),
                        new StatementSection("Long-Term Liabilities", longTermLiabilities, List.of(
                                new StatementRow("Long-Term Loans", longTermLoans),
                                new StatementRow("Other Liabilities", otherLiabilities))),
                        new StatementSection("Equity", totalEquity, List.of(
                                new StatementRow("Owner's Equity", totalEquity)))),
                liabilitiesAndEquityTotal);

        return new BalanceSheetPageResponse(
                "As of " + normalizedAsOf.format(LABEL_DATE),
                accountLabel(accountScope),
                new Metrics(
                        moneyMetric(totalAssets, previousAssets, comparisonLabel),
                        moneyMetric(totalLiabilities, previousLiabilities, comparisonLabel),
                        moneyMetric(totalEquity, previousEquity, comparisonLabel),
                        new PercentMetric(
                                debtRatio,
                                debtRatio.subtract(previousDebtRatio).setScale(2, RoundingMode.HALF_UP),
                                comparisonLabel,
                                sparkline(previousDebtRatio, debtRatio))),
                assetSide,
                liabilitiesAndEquity,
                assetComposition(cash, savings, investments, otherAssets, totalAssets),
                liabilityEquityMix(currentLiabilities, longTermLiabilities, totalEquity, liabilitiesAndEquityTotal),
                highlights(totalAssets, totalLiabilities, totalEquity, liabilitiesAndEquityTotal, currentAssets, currentLiabilities),
                keyRatios(currentAssets, currentLiabilities, totalAssets, totalLiabilities, totalEquity),
                aiSummary(totalAssets, totalLiabilities, totalEquity));
    }

    private List<Account> scopedAccounts(List<Account> accounts, String accountScope) {
        if (!hasText(accountScope) || "all".equalsIgnoreCase(accountScope)) {
            return accounts;
        }

        return accounts.stream()
                .filter(account -> accountScope.equals(stableAccountId(account))
                        || accountScope.equals(account.getAccountId())
                        || accountScope.equals(account.getPlaidAccountId())
                        || accountScope.equals(String.valueOf(account.getId())))
                .toList();
    }

    private boolean hasSnapshotHistory(List<Account> accounts) {
        return accounts.stream()
                .anyMatch(account -> accountBalanceSnapshotRepo
                        .findTopByEmailAndAccountIdOrderBySnapshotAtDesc(account.getEmail(), stableAccountId(account))
                        .isPresent());
    }

    private BigDecimal accountBalance(Account account, LocalDate asOf, boolean useSnapshots) {
        if (!useSnapshots) {
            return safeAmount(account.getBalance()).abs();
        }

        BigDecimal snapshotBalance = snapshotBalance(account, asOf);
        return snapshotBalance != null ? snapshotBalance.abs() : BigDecimal.ZERO;
    }

    private BigDecimal snapshotBalance(Account account, LocalDate asOf) {
        LocalDateTime cutoff = asOf.plusDays(1).atStartOfDay();
        return accountBalanceSnapshotRepo
                .findTopByEmailAndAccountIdAndSnapshotAtBeforeOrderBySnapshotAtDesc(
                        account.getEmail(),
                        stableAccountId(account),
                        cutoff)
                .map(AccountBalanceSnapshot::getBalance)
                .map(this::safeAmount)
                .orElse(null);
    }

    private MoneyMetric moneyMetric(BigDecimal current, BigDecimal previous, String comparisonLabel) {
        return new MoneyMetric(
                current,
                DEFAULT_CURRENCY,
                percentChange(current, previous),
                comparisonLabel,
                sparkline(previous, current));
    }

    private BigDecimal previousAssetTotal(List<Account> accounts, BigDecimal totalAssets, LocalDate comparisonDate, LocalDate asOf, boolean useSnapshots) {
        BigDecimal snapshotTotal = snapshotTotal(accounts, account -> !isLiability(account), comparisonDate);
        if (snapshotTotal != null) {
            return snapshotTotal;
        }
        return useSnapshots ? BigDecimal.ZERO : estimatePreviousAssets(accounts, totalAssets, comparisonDate, asOf);
    }

    private BigDecimal previousLiabilityTotal(List<Account> accounts, BigDecimal totalLiabilities, LocalDate comparisonDate, boolean useSnapshots) {
        BigDecimal snapshotTotal = snapshotTotal(accounts, this::isLiability, comparisonDate);
        return snapshotTotal != null ? snapshotTotal : useSnapshots ? BigDecimal.ZERO : totalLiabilities;
    }

    private BigDecimal snapshotTotal(List<Account> accounts, Predicate<Account> filter, LocalDate asOf) {
        LocalDateTime cutoff = asOf.plusDays(1).atStartOfDay();
        BigDecimal total = BigDecimal.ZERO;
        boolean foundSnapshot = false;

        for (Account account : accounts) {
            if (!filter.test(account)) {
                continue;
            }

            var snapshot = accountBalanceSnapshotRepo
                    .findTopByEmailAndAccountIdAndSnapshotAtBeforeOrderBySnapshotAtDesc(
                            account.getEmail(),
                            stableAccountId(account),
                            cutoff);

            if (snapshot.isPresent()) {
                total = total.add(safeAmount(snapshot.get().getBalance()).abs());
                foundSnapshot = true;
            }
        }

        return foundSnapshot ? total : null;
    }
    private BigDecimal estimatePreviousAssets(
            List<Account> accounts,
            BigDecimal totalAssets,
            LocalDate comparisonDate,
            LocalDate asOf) {
        BigDecimal netActivity = accounts.stream()
                .flatMap(account -> transactionsRepo.findByAccountId(account.getId()).stream())
                .filter(transaction -> transaction.getTimestamp() != null)
                .filter(transaction -> {
                    LocalDate date = transaction.getTimestamp().toLocalDate();
                    return date.isAfter(comparisonDate) && !date.isAfter(asOf);
                })
                .filter(transaction -> transaction.getType() == TransactionType.INCOME
                        || transaction.getType() == TransactionType.EXPENSE)
                .map(transaction -> {
                    BigDecimal amount = safeAmount(transaction.getAmount()).abs();
                    return transaction.getType() == TransactionType.EXPENSE ? amount.negate() : amount;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal previous = totalAssets.subtract(netActivity);
        return previous.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : previous;
    }

    private List<CompositionItem> assetComposition(
            BigDecimal cash,
            BigDecimal savings,
            BigDecimal investments,
            BigDecimal otherAssets,
            BigDecimal totalAssets) {
        return composition(List.of(
                new CompositionSource("Cash and Cash Equivalents", cash),
                new CompositionSource("Savings", savings),
                new CompositionSource("Investments", investments),
                new CompositionSource("Other Assets", otherAssets)), totalAssets, ASSET_COLORS);
    }

    private List<CompositionItem> liabilityEquityMix(
            BigDecimal currentLiabilities,
            BigDecimal longTermLiabilities,
            BigDecimal equity,
            BigDecimal total) {
        return composition(List.of(
                new CompositionSource("Current Liabilities", currentLiabilities),
                new CompositionSource("Long-Term Liabilities", longTermLiabilities),
                new CompositionSource("Equity", equity)), total, LIABILITY_COLORS);
    }

    private List<CompositionItem> composition(List<CompositionSource> rows, BigDecimal total, String[] colors) {
        List<CompositionItem> items = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            CompositionSource row = rows.get(i);
            items.add(new CompositionItem(
                    row.label(),
                    row.amount(),
                    percent(row.amount(), total),
                    colors[i % colors.length]));
        }
        return items;
    }

    private List<Highlight> highlights(
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity,
            BigDecimal liabilitiesAndEquityTotal,
            BigDecimal currentAssets,
            BigDecimal currentLiabilities) {
        List<Highlight> highlights = new ArrayList<>();

        if (totalAssets.compareTo(liabilitiesAndEquityTotal) == 0) {
            highlights.add(new Highlight(
                    "balance-sheet-balanced",
                    "Balance sheet is balanced",
                    "Assets equal liabilities plus equity.",
                    "success"));
        } else {
            highlights.add(new Highlight(
                    "balance-sheet-reconciliation",
                    "Reconciliation warning",
                    "Assets do not equal liabilities plus equity. Review account classification.",
                    "warning"));
        }

        BigDecimal debtRatio = percent(totalLiabilities, totalAssets);
        if (debtRatio.compareTo(BigDecimal.valueOf(60)) >= 0) {
            highlights.add(new Highlight(
                    "high-debt-ratio",
                    "High debt ratio",
                    "Liabilities are a large share of assets.",
                    "warning"));
        } else {
            highlights.add(new Highlight(
                    "manageable-debt-ratio",
                    "Debt ratio is manageable",
                    "Liabilities are below 60% of assets.",
                    "info"));
        }

        BigDecimal currentRatio = ratio(currentAssets, currentLiabilities);
        highlights.add(new Highlight(
                "current-ratio",
                "Current ratio",
                currentLiabilities.compareTo(BigDecimal.ZERO) == 0
                        ? "No current liabilities are recorded."
                        : "Current assets cover current liabilities " + formatRatio(currentRatio) + "x.",
                currentRatio.compareTo(BigDecimal.ONE) >= 0 ? "success" : "danger"));

        if (totalEquity.compareTo(BigDecimal.ZERO) < 0) {
            highlights.add(new Highlight(
                    "negative-equity",
                    "Negative equity",
                    "Liabilities exceed assets.",
                    "danger"));
        }

        return highlights;
    }

    private List<KeyRatio> keyRatios(
            BigDecimal currentAssets,
            BigDecimal currentLiabilities,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity) {
        return List.of(
                new KeyRatio("Current Ratio", formatRatio(ratio(currentAssets, currentLiabilities))),
                new KeyRatio("Debt Ratio", percent(totalLiabilities, totalAssets).toPlainString() + "%"),
                new KeyRatio("Equity Ratio", percent(totalEquity, totalAssets).toPlainString() + "%"),
                new KeyRatio("Working Capital", DEFAULT_CURRENCY + " " + currentAssets.subtract(currentLiabilities).setScale(2, RoundingMode.HALF_UP).toPlainString()));
    }

    private AiSummary aiSummary(BigDecimal totalAssets, BigDecimal totalLiabilities, BigDecimal totalEquity) {
        return new AiSummary(
                "Assets total " + totalAssets.setScale(2, RoundingMode.HALF_UP).toPlainString()
                        + ", liabilities total " + totalLiabilities.setScale(2, RoundingMode.HALF_UP).toPlainString()
                        + ", and equity is " + totalEquity.setScale(2, RoundingMode.HALF_UP).toPlainString() + ".",
                "AI-powered");
    }

    private BigDecimal cashAssets(List<Account> accounts, LocalDate asOf, boolean useSnapshots) {
        return accounts.stream()
                .filter(account -> contains(account.getType(), "depository")
                        || contains(account.getSubtype(), "checking")
                        || contains(account.getSubtype(), "cash management"))
                .filter(account -> !contains(account.getSubtype(), "savings"))
                .filter(account -> !isLiability(account))
                .map(account -> accountBalance(account, asOf, useSnapshots))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal savingsAssets(List<Account> accounts, LocalDate asOf, boolean useSnapshots) {
        return accounts.stream()
                .filter(account -> contains(account.getSubtype(), "savings"))
                .filter(account -> !isLiability(account))
                .map(account -> accountBalance(account, asOf, useSnapshots))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal investmentAssets(List<PlaidItem> plaidItems, List<Account> accounts, LocalDate asOf, boolean useSnapshots) {
        if (useSnapshots) {
            return accounts.stream()
                    .filter(this::isInvestmentAccount)
                    .map(account -> accountBalance(account, asOf, true))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal holdingValue = plaidItems.stream()
                .flatMap(plaidItem -> investmentHoldingRepo.findAllByPlaidItem(plaidItem).stream())
                .map(InvestmentHolding::getInstitutionValue)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (holdingValue.compareTo(BigDecimal.ZERO) > 0) {
            return holdingValue;
        }

        return accounts.stream()
                .filter(this::isInvestmentAccount)
                .map(account -> accountBalance(account, asOf, false))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal otherAssets(List<Account> accounts, LocalDate asOf, boolean useSnapshots) {
        return accounts.stream()
                .filter(account -> !isLiability(account))
                .filter(account -> !contains(account.getType(), "depository"))
                .filter(account -> !isInvestmentAccount(account))
                .filter(account -> !contains(account.getSubtype(), "checking"))
                .filter(account -> !contains(account.getSubtype(), "cash management"))
                .filter(account -> !contains(account.getSubtype(), "savings"))
                .map(account -> accountBalance(account, asOf, useSnapshots))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal creditLiabilities(List<Account> accounts, LocalDate asOf, boolean useSnapshots) {
        return accounts.stream()
                .filter(this::isCreditCardAccount)
                .map(account -> accountBalance(account, asOf, useSnapshots))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal shortTermLoanLiabilities(List<Account> accounts, LocalDate asOf, boolean useSnapshots) {
        return accounts.stream()
                .filter(account -> contains(account.getType(), "loan") || contains(account.getSubtype(), "loan"))
                .filter(account -> contains(account.getSubtype(), "short") || contains(account.getAccountName(), "short"))
                .map(account -> accountBalance(account, asOf, useSnapshots))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal longTermLoanLiabilities(List<Account> accounts, LocalDate asOf, boolean useSnapshots) {
        return accounts.stream()
                .filter(account -> contains(account.getType(), "loan") || contains(account.getSubtype(), "loan"))
                .filter(account -> !contains(account.getSubtype(), "short") && !contains(account.getAccountName(), "short"))
                .map(account -> accountBalance(account, asOf, useSnapshots))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal otherLiabilities(List<Account> accounts, LocalDate asOf, boolean useSnapshots) {
        return accounts.stream()
                .filter(this::isLiability)
                .filter(account -> !isCreditCardAccount(account))
                .filter(account -> !contains(account.getType(), "loan") && !contains(account.getSubtype(), "loan"))
                .map(account -> accountBalance(account, asOf, useSnapshots))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private boolean isInvestmentAccount(Account account) {
        return contains(account.getType(), "investment")
                || contains(account.getSubtype(), "brokerage")
                || contains(account.getSubtype(), "ira")
                || contains(account.getSubtype(), "401")
                || contains(account.getSubtype(), "tfsa")
                || contains(account.getSubtype(), "rrsp")
                || contains(account.getSubtype(), "fhsa")
                || contains(account.getSubtype(), "securities");
    }
    private boolean isLiability(Account account) {
        String accountText = accountText(account);
        return isCreditCardAccount(account)
                || accountText.contains("loan")
                || accountText.contains("mortgage")
                || accountText.contains("line of credit")
                || accountText.contains("liability");
    }

    private boolean isCreditCardAccount(Account account) {
        String accountText = accountText(account);
        if (accountText.contains("debit")) {
            return accountText.contains("credit");
        }

        return accountText.contains("credit")
                || accountText.contains("visa")
                || accountText.contains("mastercard")
                || accountText.contains("american express")
                || accountText.contains("amex");
    }

    private String accountText(Account account) {
        return String.join(" ",
                safeText(account.getType()),
                safeText(account.getSubtype()),
                safeText(account.getAccountName()),
                safeText(account.getOfficialName()),
                safeText(account.getUnofficialName()))
                .toLowerCase(Locale.US);
    }

    private BigDecimal percent(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total.abs(), 1, RoundingMode.HALF_UP);
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

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return numerator.divide(denominator.abs(), 2, RoundingMode.HALF_UP);
    }

    private List<BigDecimal> sparkline(BigDecimal start, BigDecimal end) {
        List<BigDecimal> values = new ArrayList<>();
        BigDecimal delta = end.subtract(start).divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
        for (int i = 0; i < 6; i++) {
            values.add(start.add(delta.multiply(BigDecimal.valueOf(i))).setScale(2, RoundingMode.HALF_UP));
        }
        return values;
    }

    private String formatRatio(BigDecimal ratio) {
        return ratio.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String accountLabel(String accountScope) {
        return !hasText(accountScope) || "all".equalsIgnoreCase(accountScope) ? "All accounts" : "Selected account";
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

    private boolean contains(String value, String target) {
        return value != null && value.toLowerCase(Locale.US).contains(target);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private record CompositionSource(String label, BigDecimal amount) {
    }
}
