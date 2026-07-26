package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.Accounting.app.accounts.Account;
import com.Accounting.app.accounts.AccountRepo;
import com.Accounting.app.dashboard.dto.CashFlowPageResponse;
import com.Accounting.app.dashboard.dto.CashFlowPageResponse.AiSummary;
import com.Accounting.app.dashboard.dto.CashFlowPageResponse.CashFlowChange;
import com.Accounting.app.dashboard.dto.CashFlowPageResponse.CashFlowPeriod;
import com.Accounting.app.dashboard.dto.CashFlowPageResponse.CashFlowTransaction;
import com.Accounting.app.dashboard.dto.CashFlowPageResponse.CashSnapshot;
import com.Accounting.app.dashboard.dto.CashFlowPageResponse.CategoryAmount;
import com.Accounting.app.dashboard.dto.CashFlowPageResponse.Comparisons;
import com.Accounting.app.dashboard.dto.CashFlowPageResponse.Metrics;
import com.Accounting.app.dashboard.dto.CashFlowPageResponse.Money;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.transactions.TransactionType;
import com.Accounting.app.transactions.TransactionsRepo;

@Service
public class CashFlowPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final DateTimeFormatter FULL_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final int CHANGE_LIMIT = 5;
    private static final int CATEGORY_LIMIT = 5;
    private static final int TRANSACTION_LIMIT = 25;

    private final AccountRepo accountRepo;
    private final TransactionsRepo transactionsRepo;

    public CashFlowPageServices(AccountRepo accountRepo, TransactionsRepo transactionsRepo) {
        this.accountRepo = accountRepo;
        this.transactionsRepo = transactionsRepo;
    }

    public CashFlowPageResponse cashFlowPageResponse(String email, LocalDate from, LocalDate to) {
        DateRange range = normalizeRange(from, to);
        DateRange previousRange = previousRange(range);
        List<Account> accounts = accountRepo.findAllByEmail(email);
        List<Transaction> rangeTransactions = transactionsForRange(accounts, range);
        List<Transaction> previousTransactions = transactionsForRange(accounts, previousRange);

        BigDecimal income = totalByType(rangeTransactions, TransactionType.INCOME);
        BigDecimal expenses = totalByType(rangeTransactions, TransactionType.EXPENSE);
        BigDecimal transfers = totalByType(rangeTransactions, TransactionType.TRANSFER);
        BigDecimal netCashFlow = income.subtract(expenses);

        BigDecimal previousIncome = totalByType(previousTransactions, TransactionType.INCOME);
        BigDecimal previousExpenses = totalByType(previousTransactions, TransactionType.EXPENSE);
        BigDecimal previousNetCashFlow = previousIncome.subtract(previousExpenses);

        BigDecimal endingCash = accounts.stream()
                .map(Account::getBalance)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal startingCash = endingCash.subtract(netCashFlow);

        return new CashFlowPageResponse(
                periodLabel(range),
                "All Accounts",
                new Metrics(
                        money(income),
                        money(expenses),
                        money(netCashFlow),
                        money(transfers)),
                new Comparisons(
                        percentChange(income, previousIncome),
                        percentChange(expenses, previousExpenses),
                        percentChange(netCashFlow, previousNetCashFlow),
                        "vs " + shortRangeLabel(previousRange)),
                cashFlowOverTime(rangeTransactions, range),
                new CashSnapshot(
                        money(startingCash),
                        money(netCashFlow),
                        money(endingCash),
                        "Starting Cash (" + range.from().format(SHORT_LABEL_DATE) + ")",
                        "Ending Cash (" + range.to().format(SHORT_LABEL_DATE) + ")"),
                changes(rangeTransactions, previousTransactions),
                aiSummary(income, expenses, netCashFlow, rangeTransactions),
                topCategories(rangeTransactions, TransactionType.INCOME),
                topCategories(rangeTransactions, TransactionType.EXPENSE),
                transactionRows(rangeTransactions));
    }

    private DateRange normalizeRange(LocalDate from, LocalDate to) {
        LocalDate normalizedFrom = from;
        LocalDate normalizedTo = to;

        if (normalizedFrom == null && normalizedTo == null) {
            YearMonth currentMonth = YearMonth.now();
            normalizedFrom = currentMonth.atDay(1);
            normalizedTo = currentMonth.atEndOfMonth();
        } else if (normalizedFrom == null) {
            normalizedFrom = YearMonth.from(normalizedTo).atDay(1);
        } else if (normalizedTo == null) {
            normalizedTo = YearMonth.from(normalizedFrom).atEndOfMonth();
        }

        if (normalizedFrom.isAfter(normalizedTo)) {
            return new DateRange(normalizedTo, normalizedFrom);
        }

        return new DateRange(normalizedFrom, normalizedTo);
    }

    private DateRange previousRange(DateRange range) {
        YearMonth currentMonth = YearMonth.from(range.from());
        if (range.from().equals(currentMonth.atDay(1)) && range.to().equals(currentMonth.atEndOfMonth())) {
            YearMonth previousMonth = currentMonth.minusMonths(1);
            return new DateRange(previousMonth.atDay(1), previousMonth.atEndOfMonth());
        }

        long days = range.to().toEpochDay() - range.from().toEpochDay();
        LocalDate previousTo = range.from().minusDays(1);
        return new DateRange(previousTo.minusDays(days), previousTo);
    }

    private List<Transaction> transactionsForRange(List<Account> accounts, DateRange range) {
        return accounts.stream()
                .flatMap(account -> transactionsRepo.findByAccountId(account.getId()).stream())
                .filter(transaction -> transaction.getTimestamp() != null)
                .filter(transaction -> {
                    LocalDate date = transaction.getTimestamp().toLocalDate();
                    return !date.isBefore(range.from()) && !date.isAfter(range.to());
                })
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

    private List<CashFlowPeriod> cashFlowOverTime(List<Transaction> transactions, DateRange range) {
        List<CashFlowPeriod> periods = new ArrayList<>();
        LocalDate cursor = range.from();

        while (!cursor.isAfter(range.to())) {
            LocalDate periodStart = cursor;
            LocalDate periodEnd = cursor.plusDays(6).isAfter(range.to()) ? range.to() : cursor.plusDays(6);
            List<Transaction> periodTransactions = transactions.stream()
                    .filter(transaction -> transaction.getTimestamp() != null)
                    .filter(transaction -> {
                        LocalDate date = transaction.getTimestamp().toLocalDate();
                        return !date.isBefore(periodStart) && !date.isAfter(periodEnd);
                    })
                    .toList();

            BigDecimal income = totalByType(periodTransactions, TransactionType.INCOME);
            BigDecimal expenses = totalByType(periodTransactions, TransactionType.EXPENSE);
            periods.add(new CashFlowPeriod(
                    shortRangeLabel(new DateRange(periodStart, periodEnd)),
                    income,
                    expenses,
                    income.subtract(expenses)));
            cursor = periodEnd.plusDays(1);
        }

        return periods;
    }

    private List<CashFlowChange> changes(List<Transaction> transactions, List<Transaction> previousTransactions) {
        Map<String, BigDecimal> current = netByCategory(transactions);
        Map<String, BigDecimal> previous = netByCategory(previousTransactions);
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.addAll(current.keySet());
        categories.addAll(previous.keySet());

        return categories.stream()
                .map(category -> {
                    BigDecimal delta = current.getOrDefault(category, BigDecimal.ZERO)
                            .subtract(previous.getOrDefault(category, BigDecimal.ZERO));
                    return new CategoryDelta(category, delta);
                })
                .filter(delta -> delta.amount().compareTo(BigDecimal.ZERO) != 0)
                .sorted(Comparator.comparing((CategoryDelta delta) -> delta.amount().abs()).reversed())
                .limit(CHANGE_LIMIT)
                .map(delta -> {
                    String direction = delta.amount().compareTo(BigDecimal.ZERO) >= 0 ? "up" : "down";
                    BigDecimal amount = delta.amount().abs();
                    return new CashFlowChange(
                            slug(delta.category()),
                            delta.category(),
                            ("up".equals(direction) ? "Increased by $" : "Decreased by $") + formatMoney(amount),
                            money(amount),
                            direction);
                })
                .toList();
    }

    private Map<String, BigDecimal> netByCategory(List<Transaction> transactions) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            if (transaction.getType() == TransactionType.TRANSFER) {
                continue;
            }

            String category = fallback(transaction.getDisplayCategory(), "Uncategorized");
            BigDecimal amount = safeAmount(transaction.getAmount()).abs();
            if (transaction.getType() == TransactionType.EXPENSE) {
                amount = amount.negate();
            }
            totals.merge(category, amount, BigDecimal::add);
        }
        return totals;
    }

    private List<CategoryAmount> topCategories(List<Transaction> transactions, TransactionType type) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            if (transaction.getType() != type) {
                continue;
            }
            totals.merge(
                    fallback(transaction.getDisplayCategory(), "Uncategorized"),
                    safeAmount(transaction.getAmount()).abs(),
                    BigDecimal::add);
        }

        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(CATEGORY_LIMIT)
                .map(entry -> new CategoryAmount(entry.getKey(), money(entry.getValue())))
                .toList();
    }

    private List<CashFlowTransaction> transactionRows(List<Transaction> transactions) {
        return transactions.stream()
                .sorted(Comparator.comparing(
                        Transaction::getTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TRANSACTION_LIMIT)
                .map(transaction -> new CashFlowTransaction(
                        "transaction-" + transaction.getId(),
                        transaction.getTimestamp() == null ? "" : transaction.getTimestamp().toLocalDate().toString(),
                        fallback(transaction.getDescription(), fallback(transaction.getMerchantName(), "Transaction")),
                        transactionType(transaction),
                        fallback(transaction.getDisplayCategory(), "Uncategorized"),
                        accountLabel(transaction.getAccount()),
                        money(displayTransactionAmount(transaction)),
                        transaction.getType() != TransactionType.TRANSFER,
                        transaction.getType() == TransactionType.TRANSFER ? "excluded"
                                : transaction.isPending() ? "pending" : "cleared"))
                .toList();
    }

    private BigDecimal displayTransactionAmount(Transaction transaction) {
        BigDecimal amount = safeAmount(transaction.getAmount()).abs();
        if (transaction.getType() == TransactionType.EXPENSE) {
            return amount.negate();
        }
        return amount;
    }

    private String transactionType(Transaction transaction) {
        if (transaction.getType() == null) {
            return "expense";
        }
        return transaction.getType().name().toLowerCase(Locale.US);
    }

    private String accountLabel(Account account) {
        if (account == null) {
            return "Unknown Account";
        }

        String name = fallback(account.getAccountName(), fallback(account.getOfficialName(), "Account"));
        if (account.getMask() == null || account.getMask().isBlank()) {
            return name;
        }
        return name + " **** " + account.getMask();
    }

    private AiSummary aiSummary(
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal netCashFlow,
            List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return null;
        }

        String summary = netCashFlow.compareTo(BigDecimal.ZERO) >= 0
                ? "Income exceeded expenses, producing positive net cash flow."
                : "Expenses exceeded income, producing negative net cash flow.";

        if (income.compareTo(expenses) > 0 && expenses.compareTo(BigDecimal.ZERO) > 0) {
            summary = "Income increased faster than expenses, producing positive net cash flow.";
        }

        return new AiSummary(summary, true);
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

    private String periodLabel(DateRange range) {
        return range.from().format(SHORT_LABEL_DATE) + " - " + range.to().format(FULL_LABEL_DATE);
    }

    private String shortRangeLabel(DateRange range) {
        return range.from().format(SHORT_LABEL_DATE) + " - " + range.to().format(SHORT_LABEL_DATE);
    }

    private Money money(BigDecimal amount) {
        return new Money(amount == null ? BigDecimal.ZERO : amount, DEFAULT_CURRENCY);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String formatMoney(BigDecimal amount) {
        return new DecimalFormat("#,##0.##").format(amount);
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }

    private record CategoryDelta(String category, BigDecimal amount) {
    }
}
