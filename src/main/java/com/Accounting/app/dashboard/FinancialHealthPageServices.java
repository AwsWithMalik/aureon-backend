package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.Accounting.app.accounts.Account;
import com.Accounting.app.accounts.AccountRepo;
import com.Accounting.app.auth.User;
import com.Accounting.app.auth.UserRepo;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.Alert;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.BreakdownAmount;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.CashFlow;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.CashOutAmount;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.EmergencyFund;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.Goal;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.HealthScore;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.MetricMoney;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.Metrics;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.NetWorthBreakdown;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.SpendingComparison;
import com.Accounting.app.dashboard.dto.FinancialHealthPageResponse.SparklinePoint;
import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.investments.InvestmentHolding;
import com.Accounting.app.investments.InvestmentHoldingRepo;
import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.plaid.PlaidItemRepo;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.transactions.TransactionType;
import com.Accounting.app.transactions.TransactionsRepo;

@Service
public class FinancialHealthPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
    private static final String[] CASH_OUT_COLORS = {
            "#f97316", "#64748b", "#0ea5e9", "#2563eb", "#a16207", "#475569"
    };

    private final AccountRepo accountRepo;
    private final TransactionsRepo transactionsRepo;
    private final UserRepo userRepo;
    private final PlaidItemRepo plaidItemRepo;
    private final InvestmentHoldingRepo investmentHoldingRepo;

    public FinancialHealthPageServices(
            AccountRepo accountRepo,
            TransactionsRepo transactionsRepo,
            UserRepo userRepo,
            PlaidItemRepo plaidItemRepo,
            InvestmentHoldingRepo investmentHoldingRepo) {
        this.accountRepo = accountRepo;
        this.transactionsRepo = transactionsRepo;
        this.userRepo = userRepo;
        this.plaidItemRepo = plaidItemRepo;
        this.investmentHoldingRepo = investmentHoldingRepo;
    }

    public FinancialHealthPageResponse financialHealthPageResponse(String email) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        List<Account> accounts = accountRepo.findAllByEmail(email);
        List<Transaction> currentTransactions = transactionsForMonth(accounts, currentMonth);
        List<Transaction> previousTransactions = transactionsForMonth(accounts, previousMonth);

        BigDecimal monthlyIncome = totalByType(currentTransactions, TransactionType.INCOME);
        BigDecimal monthlyExpenses = totalByType(currentTransactions, TransactionType.EXPENSE);
        BigDecimal previousIncome = totalByType(previousTransactions, TransactionType.INCOME);
        BigDecimal previousExpenses = totalByType(previousTransactions, TransactionType.EXPENSE);

        BigDecimal cashAssets = cashAssets(accounts);
        BigDecimal savingsAssets = savingsAssets(accounts);
        BigDecimal investmentAssets = investmentAssets(user, accounts);
        BigDecimal otherAssets = otherAssets(accounts);
        BigDecimal creditLiabilities = liabilitiesByKeyword(accounts, "credit");
        BigDecimal loanLiabilities = loanLiabilities(accounts);
        BigDecimal otherLiabilities = otherLiabilities(accounts);

        BigDecimal totalAssets = cashAssets.add(savingsAssets).add(investmentAssets).add(otherAssets);
        BigDecimal totalLiabilities = creditLiabilities.add(loanLiabilities).add(otherLiabilities);
        BigDecimal netWorth = totalAssets.subtract(totalLiabilities);
        BigDecimal previousNetWorth = netWorth.subtract(monthlyIncome.subtract(monthlyExpenses));
        BigDecimal emergencyMonths = monthlyExpenses.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : cashAssets.add(savingsAssets).divide(monthlyExpenses, 1, RoundingMode.HALF_UP);
        int score = calculateHealthScore(netWorth, totalAssets, totalLiabilities, monthlyIncome, monthlyExpenses, emergencyMonths);
        int previousScore = calculateHealthScore(previousNetWorth, totalAssets, totalLiabilities,
                previousIncome, previousExpenses, emergencyMonths);

        List<BreakdownAmount> assets = List.of(
                new BreakdownAmount("Cash & Checking", cashAssets),
                new BreakdownAmount("Savings", savingsAssets),
                new BreakdownAmount("Investments", investmentAssets),
                new BreakdownAmount("Other Assets", otherAssets));
        List<BreakdownAmount> liabilities = List.of(
                new BreakdownAmount("Credit Cards", creditLiabilities),
                new BreakdownAmount("Loans", loanLiabilities),
                new BreakdownAmount("Other Liabilities", otherLiabilities));

        Map<String, BigDecimal> cashIn = categoryTotals(currentTransactions, TransactionType.INCOME);
        Map<String, BigDecimal> cashOut = categoryTotals(currentTransactions, TransactionType.EXPENSE);
        Map<String, BigDecimal> previousCashOut = categoryTotals(previousTransactions, TransactionType.EXPENSE);

        return new FinancialHealthPageResponse(
                currentMonth.format(PERIOD_FORMATTER),
                "vs " + previousMonth.format(PERIOD_FORMATTER),
                DEFAULT_CURRENCY,
                new HealthScore(score, 100, determineScoreLabel(score), score - previousScore),
                new Metrics(
                        metricMoney(netWorth, netWorth, previousNetWorth),
                        metricMoney(monthlyIncome, monthlyIncome, previousIncome),
                        metricMoney(monthlyExpenses, monthlyExpenses, previousExpenses),
                        new EmergencyFund(emergencyMonths, determineEmergencyFundLabel(emergencyMonths))),
                new NetWorthBreakdown(currentMonth.atEndOfMonth().toString(), assets, liabilities),
                new CashFlow(
                        cashInRows(cashIn),
                        cashOutRows(cashOut)),
                alerts(monthlyIncome, monthlyExpenses, cashOut, previousCashOut, emergencyMonths),
                spendingComparison(cashOut, previousCashOut),
                goals(cashAssets.add(savingsAssets), monthlyExpenses, totalLiabilities));
    }

    private List<Transaction> transactionsForMonth(List<Account> accounts, YearMonth month) {
        return accounts.stream()
                .flatMap(account -> transactionsRepo.findByAccountId(account.getId()).stream())
                .filter(transaction -> transaction.getTimestamp() != null)
                .filter(transaction -> YearMonth.from(transaction.getTimestamp()).equals(month))
                .toList();
    }

    private BigDecimal totalByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == type)
                .map(Transaction::getAmount)
                .filter(amount -> amount != null)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> categoryTotals(List<Transaction> transactions, TransactionType type) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            if (transaction.getType() != type) {
                continue;
            }
            totals.merge(
                    fallback(transaction.getDisplayCategory(), type == TransactionType.INCOME ? "Income" : "Other"),
                    safeAmount(transaction.getAmount()).abs(),
                    BigDecimal::add);
        }
        return totals;
    }

    private List<BreakdownAmount> cashInRows(Map<String, BigDecimal> totals) {
        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new BreakdownAmount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<CashOutAmount> cashOutRows(Map<String, BigDecimal> totals) {
        List<Map.Entry<String, BigDecimal>> entries = totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(6)
                .toList();
        List<CashOutAmount> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, BigDecimal> entry = entries.get(i);
            rows.add(new CashOutAmount(entry.getKey(), entry.getValue(), CASH_OUT_COLORS[i % CASH_OUT_COLORS.length]));
        }
        return rows;
    }

    private List<SpendingComparison> spendingComparison(
            Map<String, BigDecimal> current,
            Map<String, BigDecimal> previous) {
        return current.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new SpendingComparison(
                        entry.getKey(),
                        entry.getValue(),
                        percentChange(entry.getValue(), previous.getOrDefault(entry.getKey(), BigDecimal.ZERO))))
                .toList();
    }

    private List<Alert> alerts(
            BigDecimal income,
            BigDecimal expenses,
            Map<String, BigDecimal> cashOut,
            Map<String, BigDecimal> previousCashOut,
            BigDecimal emergencyMonths) {
        List<Alert> alerts = new ArrayList<>();
        spendingComparison(cashOut, previousCashOut).stream()
                .filter(item -> item.changePercent().compareTo(BigDecimal.TEN) > 0)
                .findFirst()
                .ifPresent(item -> alerts.add(new Alert(
                        "high-spending",
                        "High spending detected",
                        item.label() + " spending is " + item.changePercent().abs() + "% higher than last month.",
                        determineAlertSeverity(AlertSignal.HIGH_SPENDING))));

        if (expenses.compareTo(income) > 0) {
            alerts.add(new Alert(
                    "negative-cash-flow",
                    "Negative cash flow",
                    "Expenses are higher than income this month.",
                    determineAlertSeverity(AlertSignal.NEGATIVE_CASH_FLOW)));
        }

        if (emergencyMonths.compareTo(BigDecimal.valueOf(3)) < 0) {
            alerts.add(new Alert(
                    "emergency-fund",
                    "Emergency fund needs attention",
                    "Current liquid savings cover less than 3 months of expenses.",
                    determineAlertSeverity(AlertSignal.EMERGENCY_FUND_LOW)));
        }

        if (alerts.isEmpty()) {
            alerts.add(new Alert(
                    "positive-progress",
                    "Positive cash flow",
                    "Income is covering expenses for the current month.",
                    determineAlertSeverity(AlertSignal.POSITIVE_PROGRESS)));
        }

        return alerts;
    }

    private List<Goal> goals(BigDecimal liquidAssets, BigDecimal monthlyExpenses, BigDecimal liabilities) {
        BigDecimal emergencyTarget = monthlyExpenses.multiply(BigDecimal.valueOf(6));
        if (emergencyTarget.compareTo(BigDecimal.ZERO) == 0) {
            emergencyTarget = BigDecimal.valueOf(12000);
        }
        BigDecimal debtTarget = liabilities.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.valueOf(5000) : liabilities;
        BigDecimal debtProgress = debtTarget.subtract(liabilities.min(debtTarget));

        return List.of(
                new Goal(
                        "emergency-fund",
                        "Emergency Fund",
                        liquidAssets,
                        emergencyTarget,
                        progress(liquidAssets, emergencyTarget),
                        determineGoalStatus(progress(liquidAssets, emergencyTarget))),
                new Goal(
                        "debt-reduction",
                        "Debt Reduction",
                        debtProgress,
                        debtTarget,
                        progress(debtProgress, debtTarget),
                        determineGoalStatus(progress(debtProgress, debtTarget))));
    }

    private MetricMoney metricMoney(BigDecimal amount, BigDecimal current, BigDecimal previous) {
        return new MetricMoney(
                amount,
                DEFAULT_CURRENCY,
                percentChange(current, previous),
                sparkline(previous, current));
    }

    private List<SparklinePoint> sparkline(BigDecimal previous, BigDecimal current) {
        BigDecimal middle = previous.add(current).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return List.of(new SparklinePoint(previous), new SparklinePoint(middle), new SparklinePoint(current));
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private int calculateHealthScore(
            BigDecimal netWorth,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal emergencyMonths) {
        int score = 45;
        if (netWorth.compareTo(BigDecimal.ZERO) > 0) {
            score += 15;
        }
        if (income.compareTo(expenses) > 0) {
            score += 15;
        }
        score += Math.min(15, emergencyMonths.multiply(BigDecimal.valueOf(3)).intValue());
        if (totalAssets.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal debtRatio = totalLiabilities.divide(totalAssets, 2, RoundingMode.HALF_UP);
            if (debtRatio.compareTo(BigDecimal.valueOf(0.25)) < 0) {
                score += 10;
            } else if (debtRatio.compareTo(BigDecimal.valueOf(0.5)) < 0) {
                score += 5;
            }
        }
        return Math.max(0, Math.min(100, score));
    }

    private String determineScoreLabel(int score) {
        if (score >= 80) {
            return "Excellent";
        }
        if (score >= 65) {
            return "Good";
        }
        if (score >= 45) {
            return "Fair";
        }
        return "Needs attention";
    }

    private String determineEmergencyFundLabel(BigDecimal monthsCovered) {
        if (monthsCovered.compareTo(BigDecimal.valueOf(6)) >= 0) {
            return "Excellent";
        }
        if (monthsCovered.compareTo(BigDecimal.valueOf(3)) >= 0) {
            return "Good";
        }
        if (monthsCovered.compareTo(BigDecimal.ONE) >= 0) {
            return "Fair";
        }
        return "Needs attention";
    }

    private int progress(BigDecimal current, BigDecimal target) {
        if (target.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, current
                .divide(target, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .intValue()));
    }

    private String determineGoalStatus(int progress) {
        if (progress >= 75) {
            return "on_track";
        }
        if (progress >= 40) {
            return "watch";
        }
        return "needs_attention";
    }

    private String determineAlertSeverity(AlertSignal signal) {
        return switch (signal) {
            case HIGH_SPENDING, NEGATIVE_CASH_FLOW -> "warning";
            case EMERGENCY_FUND_LOW, POSITIVE_PROGRESS -> "info";
        };
    }

    private BigDecimal cashAssets(List<Account> accounts) {
        return accounts.stream()
                .filter(account -> contains(account.getType(), "depository")
                        || contains(account.getSubtype(), "checking")
                        || contains(account.getSubtype(), "cash management"))
                .filter(account -> !contains(account.getSubtype(), "savings"))
                .map(Account::getBalance)
                .filter(amount -> amount != null)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal savingsAssets(List<Account> accounts) {
        return accounts.stream()
                .filter(account -> contains(account.getSubtype(), "savings"))
                .map(Account::getBalance)
                .filter(amount -> amount != null)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal investmentAssets(User user, List<Account> accounts) {
        BigDecimal holdingValue = plaidItemRepo.findByUserId(user.getUserId())
                .map(this::holdingValue)
                .orElse(BigDecimal.ZERO);
        if (holdingValue.compareTo(BigDecimal.ZERO) > 0) {
            return holdingValue;
        }
        return accounts.stream()
                .filter(account -> contains(account.getType(), "investment")
                        || contains(account.getSubtype(), "brokerage")
                        || contains(account.getSubtype(), "ira")
                        || contains(account.getSubtype(), "401"))
                .map(Account::getBalance)
                .filter(amount -> amount != null)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal holdingValue(PlaidItem plaidItem) {
        return investmentHoldingRepo.findAllByPlaidItem(plaidItem).stream()
                .map(InvestmentHolding::getInstitutionValue)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal otherAssets(List<Account> accounts) {
        return accounts.stream()
                .filter(account -> !isLiability(account))
                .filter(account -> !contains(account.getType(), "depository"))
                .filter(account -> !contains(account.getType(), "investment"))
                .map(Account::getBalance)
                .filter(amount -> amount != null)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal liabilitiesByKeyword(List<Account> accounts, String keyword) {
        return accounts.stream()
                .filter(account -> contains(account.getType(), keyword) || contains(account.getSubtype(), keyword))
                .map(Account::getBalance)
                .filter(amount -> amount != null)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal loanLiabilities(List<Account> accounts) {
        return accounts.stream()
                .filter(account -> contains(account.getType(), "loan") || contains(account.getSubtype(), "loan"))
                .map(Account::getBalance)
                .filter(amount -> amount != null)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal otherLiabilities(List<Account> accounts) {
        return accounts.stream()
                .filter(this::isLiability)
                .filter(account -> !contains(account.getType(), "credit") && !contains(account.getSubtype(), "credit"))
                .filter(account -> !contains(account.getType(), "loan") && !contains(account.getSubtype(), "loan"))
                .map(Account::getBalance)
                .filter(amount -> amount != null)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isLiability(Account account) {
        return contains(account.getType(), "credit")
                || contains(account.getType(), "loan")
                || contains(account.getSubtype(), "credit")
                || contains(account.getSubtype(), "loan");
    }

    private boolean contains(String value, String target) {
        return value != null && value.toLowerCase(Locale.US).contains(target);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private enum AlertSignal {
        HIGH_SPENDING,
        NEGATIVE_CASH_FLOW,
        EMERGENCY_FUND_LOW,
        POSITIVE_PROGRESS
    }
}
