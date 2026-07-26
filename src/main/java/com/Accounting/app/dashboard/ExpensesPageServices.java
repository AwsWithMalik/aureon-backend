package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.Accounting.app.accounts.Account;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.transactions.TransactionType;
import com.Accounting.app.dashboard.dto.ExpensesPageResponse;
import com.Accounting.app.accounts.dto.Balance;
import com.Accounting.app.dashboard.dto.Approval;
import com.Accounting.app.dashboard.dto.CategorySpend;
import com.Accounting.app.dashboard.dto.RecurringTool;
import com.Accounting.app.dashboard.dto.SpendTrend;
import com.Accounting.app.accounts.AccountRepo;
import com.Accounting.app.transactions.TransactionsRepo;

@Service
public class ExpensesPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final AccountRepo accountRepo;
    private final TransactionsRepo transactionsRepo;

    public ExpensesPageServices(AccountRepo accountRepo, TransactionsRepo transactionsRepo) {
        this.accountRepo = accountRepo;
        this.transactionsRepo = transactionsRepo;
    }

    public ExpensesPageResponse expensesPageResponse(String email, LocalDate from, LocalDate to) {
        List<Account> accounts = accountRepo.findAllByEmail(email);

        List<Transaction> expenses = getExpenseTransactions(accounts, from, to);

        return new ExpensesPageResponse(
                calculateSpendTrend(expenses),
                calculateApprovals(),
                calculateCategorySpend(expenses),
                calculateRecurringTools());
    }

    public List<SpendTrend> calculateSpendTrend(List<Transaction> expenses) {
        Map<YearMonth, BigDecimal> spendByMonth = new LinkedHashMap<>();

        expenses.stream()
                .filter(transaction -> transaction.getTimestamp() != null)
                .sorted(Comparator.comparing(Transaction::getTimestamp))
                .forEach(transaction -> {
                    YearMonth month = YearMonth.from(transaction.getTimestamp());
                    spendByMonth.merge(month, normalizeAmount(transaction.getAmount()), BigDecimal::add);
                });

        return spendByMonth.entrySet().stream()
                .map(entry -> new SpendTrend(
                        entry.getKey().format(MONTH_FORMATTER),
                        entry.getValue(),
                        entry.getValue()))
                .toList();
    }

    public List<CategorySpend> calculateCategorySpend(List<Transaction> expenses) {
        Map<String, BigDecimal> spendByCategory = new LinkedHashMap<>();

        for (Transaction transaction : expenses) {
            String category = fallback(transaction.getDisplayCategory(), "Uncategorized");
            spendByCategory.merge(category, normalizeAmount(transaction.getAmount()), BigDecimal::add);
        }

        List<CategorySpend> categorySpend = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : spendByCategory.entrySet()) {
            categorySpend.add(new CategorySpend(
                    toId(entry.getKey()),
                    entry.getKey(),
                    new Balance(entry.getValue(), DEFAULT_CURRENCY)));
        }

        return categorySpend;
    }

    public List<Approval> calculateApprovals() {
        return new ArrayList<>();
    }

    public List<RecurringTool> calculateRecurringTools() {
        return new ArrayList<>();
    }

    private List<Transaction> getExpenseTransactions(List<Account> accounts, LocalDate from, LocalDate to) {
        return accounts.stream()
                .filter(account -> account.getId() != null)
                .flatMap(account -> transactionsRepo.findByAccountId(account.getId()).stream())
                .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
                .filter(transaction -> isWithinRange(transaction, from, to))
                .sorted(Comparator.comparing(
                        Transaction::getTimestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private boolean isWithinRange(Transaction transaction, LocalDate from, LocalDate to) {
        if (transaction.getTimestamp() == null) {
            return from == null && to == null;
        }

        LocalDate date = transaction.getTimestamp().toLocalDate();

        if (from != null && date.isBefore(from)) {
            return false;
        }

        return to == null || !date.isAfter(to);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount != null ? amount.abs() : BigDecimal.ZERO;
    }

    private String toId(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String fallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
