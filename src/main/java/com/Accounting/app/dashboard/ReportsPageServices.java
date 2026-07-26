package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.accounts.Account;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.transactions.TransactionType;
import com.Accounting.app.auth.User;
import com.Accounting.app.dashboard.dto.ReportsPageResponse;
import com.Accounting.app.dashboard.dto.ComplianceCalendar;
import com.Accounting.app.dashboard.dto.KpiDelta;
import com.Accounting.app.dashboard.dto.MarginTrend;
import com.Accounting.app.dashboard.dto.SavedReport;
import com.Accounting.app.accounts.AccountRepo;
import com.Accounting.app.transactions.TransactionsRepo;
import com.Accounting.app.auth.UserRepo;

@Service
public class ReportsPageServices {
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final UserRepo userRepo;
    private final AccountRepo accountRepo;
    private final TransactionsRepo transactionsRepo;

    public ReportsPageServices(UserRepo userRepo, AccountRepo accountRepo, TransactionsRepo transactionsRepo) {
        this.userRepo = userRepo;
        this.accountRepo = accountRepo;
        this.transactionsRepo = transactionsRepo;
    }

    public ReportsPageResponse reportsPageResponse(String email, LocalDate from, LocalDate to) {
        User user = userRepo.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found"));
        List<Account> accounts = accountRepo.findAllByEmail(email);
        List<Transaction> transactions = getTransactionsForAccounts(accounts).stream()
                .filter(transaction -> isWithinRange(transaction, from, to))
                .toList();

        List<MarginTrend> marginTrend = calculateMarginTrend(transactions);

        return new ReportsPageResponse(
                marginTrend,
                calculateKpiDeltas(transactions),
                calculateSavedReports(user, transactions),
                calculateComplianceCalendar());
    }

    public List<MarginTrend> calculateMarginTrend(List<Transaction> transactions) {
        Map<YearMonth, MonthlyTotals> totalsByMonth = new LinkedHashMap<>();

        transactions.stream()
                .filter(transaction -> transaction.getTimestamp() != null)
                .sorted(Comparator.comparing(Transaction::getTimestamp))
                .forEach(transaction -> {
                    YearMonth month = YearMonth.from(transaction.getTimestamp());
                    MonthlyTotals totals = totalsByMonth.computeIfAbsent(month, key -> new MonthlyTotals());

                    if (transaction.getType() == TransactionType.INCOME) {
                        totals.gross = totals.gross.add(normalizeAmount(transaction.getAmount()));
                    } else if (transaction.getType() == TransactionType.EXPENSE) {
                        totals.expense = totals.expense.add(normalizeAmount(transaction.getAmount()));
                    }
                });

        return totalsByMonth.entrySet().stream()
                .map(entry -> new MarginTrend(
                        entry.getKey().format(MONTH_FORMATTER),
                        entry.getValue().gross,
                        entry.getValue().gross.subtract(entry.getValue().expense)))
                .toList();
    }

    public List<KpiDelta> calculateKpiDeltas(List<Transaction> transactions) {
        BigDecimal gross = totalByType(transactions, TransactionType.INCOME);
        BigDecimal expenses = totalByType(transactions, TransactionType.EXPENSE);
        BigDecimal net = gross.subtract(expenses);
        BigDecimal marginPercent = gross.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : net.divide(gross, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        return List.of(
                new KpiDelta("gross-revenue", "Gross revenue", moneyValue(gross), direction(gross), tone(gross, true)),
                new KpiDelta("net-margin", "Net margin", percentValue(marginPercent), direction(marginPercent),
                        tone(marginPercent, true)),
                new KpiDelta("expenses", "Expenses", moneyValue(expenses), direction(expenses), tone(expenses, false)),
                new KpiDelta("net-profit", "Net profit", moneyValue(net), direction(net), tone(net, true)));
    }

    public List<SavedReport> calculateSavedReports(User user, List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return List.of();
        }

        String updatedAt = transactions.stream()
                .map(Transaction::getTimestamp)
                .filter(timestamp -> timestamp != null)
                .max(Comparator.naturalOrder())
                .map(timestamp -> timestamp.toLocalDate().toString())
                .orElse(LocalDate.now().toString());
        String owner = fallback(user.getName(), user.getEmail());

        return List.of(
                new SavedReport("profit-loss", "Profit and loss", owner, "monthly", updatedAt),
                new SavedReport("cashflow-summary", "Cashflow summary", owner, "weekly", updatedAt),
                new SavedReport("tax-readiness", "Tax readiness", owner, "quarterly", updatedAt));
    }

    public List<ComplianceCalendar> calculateComplianceCalendar() {
        int year = LocalDate.now().getYear();

        return List.of(
                new ComplianceCalendar("quarterly-estimate-q1", "Quarterly tax estimate Q1",
                        LocalDate.of(year, 3, 15).toString(), statusFor(LocalDate.of(year, 3, 15))),
                new ComplianceCalendar("quarterly-estimate-q2", "Quarterly tax estimate Q2",
                        LocalDate.of(year, 6, 15).toString(), statusFor(LocalDate.of(year, 6, 15))),
                new ComplianceCalendar("quarterly-estimate-q3", "Quarterly tax estimate Q3",
                        LocalDate.of(year, 9, 15).toString(), statusFor(LocalDate.of(year, 9, 15))),
                new ComplianceCalendar("year-end-close", "Year-end close",
                        LocalDate.of(year, 12, 31).toString(), statusFor(LocalDate.of(year, 12, 31))));
    }

    private List<Transaction> getTransactionsForAccounts(List<Account> accounts) {
        return accounts.stream()
                .filter(account -> account.getId() != null)
                .flatMap(account -> transactionsRepo.findByAccountId(account.getId()).stream())
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

    private BigDecimal totalByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == type)
                .map(Transaction::getAmount)
                .map(this::normalizeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount != null ? amount.abs() : BigDecimal.ZERO;
    }

    private String moneyValue(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String percentValue(BigDecimal amount) {
        return amount.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String direction(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return "up";
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return "down";
        }

        return "flat";
    }

    private String tone(BigDecimal amount, boolean positiveIsGood) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return "slate";
        }

        boolean positive = amount.compareTo(BigDecimal.ZERO) > 0;
        return positive == positiveIsGood ? "emerald" : "rose";
    }

    private String statusFor(LocalDate dueDate) {
        LocalDate today = LocalDate.now();

        if (today.isAfter(dueDate)) {
            return "in_review";
        }

        if (!today.plusDays(30).isBefore(dueDate)) {
            return "upcoming";
        }

        return "on_track";
    }

    private String fallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private static class MonthlyTotals {
        private BigDecimal gross = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;
    }
}
