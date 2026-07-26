package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.Accounting.app.accounts.Account;
import com.Accounting.app.accounts.AccountRepo;
import com.Accounting.app.dashboard.dto.OverviewPageResponse;
import com.Accounting.app.dashboard.dto.OverviewPageResponse.AiSummary;
import com.Accounting.app.dashboard.dto.OverviewPageResponse.CashFlow;
import com.Accounting.app.dashboard.dto.OverviewPageResponse.Comparisons;
import com.Accounting.app.dashboard.dto.OverviewPageResponse.ExpenseBreakdownItem;
import com.Accounting.app.dashboard.dto.OverviewPageResponse.InvoiceSummaryItem;
import com.Accounting.app.dashboard.dto.OverviewPageResponse.MonthlyRevenuePoint;
import com.Accounting.app.dashboard.dto.OverviewPageResponse.Money;
import com.Accounting.app.dashboard.dto.OverviewPageResponse.RecentTransactionItem;
import com.Accounting.app.dashboard.dto.OverviewPageResponse.ReviewQueueItem;
import com.Accounting.app.dashboard.dto.OverviewPageResponse.SparklinePoint;
import com.Accounting.app.files.FileRepo;
import com.Accounting.app.files.UploadedFile;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.transactions.TransactionType;
import com.Accounting.app.transactions.TransactionsRepo;

@Service
public class OverviewPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final int RECENT_TRANSACTION_LIMIT = 5;
    private static final String[] CHART_COLORS = {
            "#10b981", "#06b6d4", "#f59e0b", "#8b5cf6", "#ef4444", "#14b8a6"
    };
    private static final DateTimeFormatter FULL_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM", Locale.US);

    private final TransactionsRepo transactionsRepo;
    private final AccountRepo accountRepo;
    private final FileRepo fileRepo;

    public OverviewPageServices(
            TransactionsRepo transactionsRepo,
            AccountRepo accountRepo,
            FileRepo fileRepo) {
        this.transactionsRepo = transactionsRepo;
        this.accountRepo = accountRepo;
        this.fileRepo = fileRepo;
    }

    public OverviewPageResponse overviewPageResponse(String email, LocalDate from, LocalDate to) {
        List<Account> accounts = accountRepo.findAllByEmail(email);
        List<Transaction> transactions = getTransactionsForAccounts(accounts);
        DateRange range = resolveRange(transactions, from, to);
        DateRange previousRange = previousRange(range);

        List<Transaction> rangeTransactions = transactionsInRange(transactions, range);
        List<Transaction> previousTransactions = transactionsInRange(transactions, previousRange);
        List<UploadedFile> userFiles = fileRepo.findAllByUser_Email(email);

        BigDecimal inflow = totalByType(rangeTransactions, TransactionType.INCOME);
        BigDecimal outflow = totalByType(rangeTransactions, TransactionType.EXPENSE);
        BigDecimal previousInflow = totalByType(previousTransactions, TransactionType.INCOME);
        BigDecimal previousOutflow = totalByType(previousTransactions, TransactionType.EXPENSE);
        BigDecimal netProfit = inflow.subtract(outflow).setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousNetProfit = previousInflow.subtract(previousOutflow).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalBalance = totalBalance(accounts);
        BigDecimal previousBalance = estimatedPreviousBalance(totalBalance, netProfit);

        List<MonthlyRevenuePoint> monthlyRevenue = monthlyRevenue(range, transactions);
        List<ExpenseBreakdownItem> expenseBreakdown = expenseBreakdown(rangeTransactions);
        List<RecentTransactionItem> recentTransactions = recentTransactions(rangeTransactions);
        List<ReviewQueueItem> reviewQueue = reviewQueue(transactions, userFiles);

        return new OverviewPageResponse(
                periodLabel(range),
                "vs " + periodLabel(previousRange),
                money(totalBalance),
                money(netProfit),
                new CashFlow(
                        money(inflow),
                        money(outflow)),
                new Comparisons(
                        percentChange(totalBalance, previousBalance),
                        percentChange(netProfit, previousNetProfit),
                        percentChange(inflow, previousInflow),
                        percentChange(outflow, previousOutflow)),
                monthlyRevenue,
                profitSparkline(monthlyRevenue),
                expenseBreakdown,
                recentTransactions,
                invoiceSummary(rangeTransactions, range),
                reviewQueue,
                aiSummary(inflow, outflow, netProfit, expenseBreakdown, reviewQueue.size(), range));
    }

    private DateRange resolveRange(List<Transaction> transactions, LocalDate from, LocalDate to) {
        if (from != null || to != null) {
            LocalDate normalizedTo = to != null ? min(to, LocalDate.now()) : min(YearMonth.from(from).atEndOfMonth(), LocalDate.now());
            LocalDate normalizedFrom = from != null ? from : YearMonth.from(normalizedTo).atDay(1);
            return normalizedFrom.isAfter(normalizedTo)
                    ? new DateRange(normalizedTo, normalizedFrom)
                    : new DateRange(normalizedFrom, normalizedTo);
        }

        LocalDate latestDate = transactions.stream()
                .map(Transaction::getTimestamp)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .max(Comparator.naturalOrder())
                .orElse(LocalDate.now());
        YearMonth latestMonth = YearMonth.from(min(latestDate, LocalDate.now()));
        return new DateRange(latestMonth.atDay(1), min(latestMonth.atEndOfMonth(), LocalDate.now()));
    }

    private DateRange previousRange(DateRange range) {
        long days = range.to().toEpochDay() - range.from().toEpochDay();
        LocalDate previousTo = range.from().minusDays(1);
        return new DateRange(previousTo.minusDays(days), previousTo);
    }

    private List<Transaction> transactionsInRange(List<Transaction> transactions, DateRange range) {
        return transactions.stream()
                .filter(transaction -> transaction.getTimestamp() != null)
                .filter(transaction -> {
                    LocalDate date = transaction.getTimestamp().toLocalDate();
                    return !date.isBefore(range.from()) && !date.isAfter(range.to());
                })
                .toList();
    }

    private BigDecimal totalBalance(List<Account> accounts) {
        return accounts.stream()
                .map(Account::getBalance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal estimatedPreviousBalance(BigDecimal currentBalance, BigDecimal currentNetProfit) {
        return currentBalance.subtract(currentNetProfit).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal totalByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == type)
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<MonthlyRevenuePoint> monthlyRevenue(DateRange range, List<Transaction> transactions) {
        List<MonthlyRevenuePoint> points = new ArrayList<>();
        LocalDate endCap = min(range.to(), LocalDate.now());
        YearMonth startMonth = YearMonth.from(range.from());
        YearMonth endMonth = YearMonth.from(endCap);

        for (YearMonth month = startMonth; !month.isAfter(endMonth); month = month.plusMonths(1)) {
            LocalDate monthStart = month.atDay(1);
            LocalDate monthEnd = min(month.atEndOfMonth(), endCap);
            List<Transaction> monthlyTransactions = transactions.stream()
                    .filter(transaction -> transaction.getTimestamp() != null)
                    .filter(transaction -> {
                        LocalDate date = transaction.getTimestamp().toLocalDate();
                        return !date.isBefore(monthStart) && !date.isAfter(monthEnd);
                    })
                    .toList();
            BigDecimal inflow = totalByType(monthlyTransactions, TransactionType.INCOME);
            BigDecimal outflow = totalByType(monthlyTransactions, TransactionType.EXPENSE);
            points.add(new MonthlyRevenuePoint(month.format(MONTH_LABEL), inflow.subtract(outflow).setScale(2, RoundingMode.HALF_UP)));
        }

        if (points.isEmpty()) {
            points.add(new MonthlyRevenuePoint(YearMonth.from(range.to()).format(MONTH_LABEL), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
        }

        return points;
    }

    private List<SparklinePoint> profitSparkline(List<MonthlyRevenuePoint> monthlyRevenue) {
        return monthlyRevenue.stream()
                .map(point -> new SparklinePoint(point.value()))
                .toList();
    }

    private List<ExpenseBreakdownItem> expenseBreakdown(List<Transaction> transactions) {
        Map<String, BigDecimal> totalsByCategory = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            if (transaction.getType() != TransactionType.EXPENSE) {
                continue;
            }

            String category = fallback(transaction.getDisplayCategory(), "Uncategorized");
            totalsByCategory.merge(category, normalizeAmount(transaction.getAmount()), BigDecimal::add);
        }

        List<ExpenseBreakdownItem> breakdown = new ArrayList<>();
        int colorIndex = 0;
        for (Map.Entry<String, BigDecimal> entry : totalsByCategory.entrySet()) {
            breakdown.add(new ExpenseBreakdownItem(
                    entry.getKey(),
                    entry.getValue().setScale(2, RoundingMode.HALF_UP),
                    CHART_COLORS[colorIndex % CHART_COLORS.length]));
            colorIndex++;
        }

        return breakdown;
    }

    private List<RecentTransactionItem> recentTransactions(List<Transaction> transactions) {
        return transactions.stream()
                .sorted(Comparator.comparing(
                        Transaction::getTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_TRANSACTION_LIMIT)
                .map(this::recentTransaction)
                .toList();
    }

    private RecentTransactionItem recentTransaction(Transaction transaction) {
        BigDecimal amount = normalizeAmount(transaction.getAmount());
        if (transaction.getType() == TransactionType.EXPENSE) {
            amount = amount.negate();
        }

        return new RecentTransactionItem(
                transactionId(transaction),
                fallback(transaction.getMerchantName(), fallback(transaction.getDescription(), "Transaction")),
                fallback(transaction.getDisplayCategory(), "Uncategorized"),
                amount.setScale(2, RoundingMode.HALF_UP),
                transactionStatus(transaction),
                transaction.getTimestamp() != null ? transaction.getTimestamp().toString() : LocalDateTime.now().toString());
    }

    private List<InvoiceSummaryItem> invoiceSummary(List<Transaction> transactions, DateRange range) {
        int paid = (int) transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.INCOME)
                .filter(transaction -> !transaction.isPending())
                .count();
        int pending = (int) transactions.stream()
                .filter(Transaction::isPending)
                .count();
        int overdue = (int) transactions.stream()
                .filter(Transaction::isPending)
                .filter(transaction -> transaction.getTimestamp() != null)
                .filter(transaction -> transaction.getTimestamp().toLocalDate().isBefore(range.to().minusDays(7)))
                .count();
        return List.of(
                new InvoiceSummaryItem("Paid", paid),
                new InvoiceSummaryItem("Pending", pending),
                new InvoiceSummaryItem("Overdue", overdue));
    }

    private List<ReviewQueueItem> reviewQueue(List<Transaction> transactions, List<UploadedFile> userFiles) {
        List<ReviewQueueItem> items = new ArrayList<>();
        String today = LocalDate.now().toString();

        long pendingTransactions = transactions.stream().filter(Transaction::isPending).count();
        if (pendingTransactions > 0) {
            items.add(reviewQueueItem(
                    ReviewQueueSignal.PENDING_TRANSACTIONS,
                    "pending-transactions",
                    "Pending transactions",
                    pendingTransactions + " transactions are still pending settlement.",
                    latestTransactionDate(transactions, Transaction::isPending, today)));
        }

        long uncategorizedExpenses = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
                .filter(transaction -> transaction.getDisplayCategory() == null || transaction.getDisplayCategory().isBlank()
                        || "uncategorized".equalsIgnoreCase(transaction.getDisplayCategory()))
                .count();
        if (uncategorizedExpenses > 0) {
            items.add(reviewQueueItem(
                    ReviewQueueSignal.UNCATEGORIZED_EXPENSES,
                    "uncategorized-expenses",
                    "Uncategorized expenses",
                    uncategorizedExpenses + " expenses still need category review.",
                    latestTransactionDate(transactions, transaction -> transaction.getType() == TransactionType.EXPENSE
                            && (transaction.getDisplayCategory() == null
                                    || transaction.getDisplayCategory().isBlank()
                                    || "uncategorized".equalsIgnoreCase(transaction.getDisplayCategory())),
                            today)));
        }

        long filesNeedingReview = userFiles.stream()
                .filter(file -> file.getStatus() == null
                        || file.getStatus().isBlank()
                        || !"extracted".equalsIgnoreCase(file.getStatus()))
                .count();
        if (filesNeedingReview > 0) {
            items.add(reviewQueueItem(
                    ReviewQueueSignal.FILES_NEEDING_REVIEW,
                    "files-needing-review",
                    "Files awaiting review",
                    filesNeedingReview + " uploaded files still need extraction or manual review.",
                    latestFileDate(userFiles, today)));
        }

        if (items.isEmpty()) {
            items.add(reviewQueueItem(
                    ReviewQueueSignal.CLEAR,
                    "overview-clear",
                    "Review queue is clear",
                    "No items need immediate follow-up.",
                    today));
        }

        return items;
    }

    private String latestTransactionDate(
            List<Transaction> transactions,
            java.util.function.Predicate<Transaction> predicate,
            String fallback) {
        return transactions.stream()
                .filter(predicate)
                .map(Transaction::getTimestamp)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .max(Comparator.naturalOrder())
                .map(LocalDate::toString)
                .orElse(fallback);
    }

    private String latestFileDate(List<UploadedFile> userFiles, String fallback) {
        return userFiles.stream()
                .filter(file -> file.getStatus() == null
                        || file.getStatus().isBlank()
                        || !"extracted".equalsIgnoreCase(file.getStatus()))
                .map(UploadedFile::getUploadedAt)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .max(Comparator.naturalOrder())
                .map(LocalDate::toString)
                .orElse(fallback);
    }

    private ReviewQueueItem reviewQueueItem(
            ReviewQueueSignal signal,
            String id,
            String label,
            String detail,
            String date) {
        ReviewQueuePresentation presentation = determineReviewQueuePresentation(signal);
        return new ReviewQueueItem(
                id,
                label,
                detail,
                date,
                presentation.priority(),
                presentation.tone(),
                presentation.icon(),
                presentation.actionLabel(),
                presentation.route());
    }

    private ReviewQueuePresentation determineReviewQueuePresentation(ReviewQueueSignal signal) {
        return switch (signal) {
            case UNCATEGORIZED_EXPENSES -> new ReviewQueuePresentation(
                    "High",
                    "indigo",
                    "receipt",
                    "Categorize expenses",
                    "/dashboard/expense-breakdown");
            case PENDING_TRANSACTIONS -> new ReviewQueuePresentation(
                    "Medium",
                    "amber",
                    "alertTriangle",
                    "Review transactions",
                    "/dashboard/transactions");
            case FILES_NEEDING_REVIEW -> new ReviewQueuePresentation(
                    "Medium",
                    "green",
                    "fileText",
                    "Open files",
                    "/dashboard/files");
            case CLEAR -> new ReviewQueuePresentation(
                    "Low",
                    "green",
                    "sparkles",
                    "Open dashboard",
                    "/dashboard/overview");
        };
    }

    private AiSummary aiSummary(
            BigDecimal inflow,
            BigDecimal outflow,
            BigDecimal netProfit,
            List<ExpenseBreakdownItem> expenseBreakdown,
            int reviewQueueCount,
            DateRange range) {
        ExpenseBreakdownItem topExpense = expenseBreakdown.stream()
                .max(Comparator.comparing(ExpenseBreakdownItem::value))
                .orElse(null);

        List<String> points = new ArrayList<>();
        points.add("Income for " + periodLabel(range) + " was " + DEFAULT_CURRENCY + " " + inflow.toPlainString() + ".");
        points.add("Expenses totaled " + DEFAULT_CURRENCY + " " + outflow.toPlainString() + ".");
        if (topExpense != null) {
            points.add("Largest expense category was " + topExpense.name() + " at " + DEFAULT_CURRENCY + " " + topExpense.value().toPlainString() + ".");
        }
        if (reviewQueueCount > 0) {
            points.add(reviewQueueCount + " review items are still open.");
        }

        String summary = netProfit.compareTo(BigDecimal.ZERO) >= 0
                ? "Net profit remained positive for the selected period."
                : "Expenses outpaced income in the selected period.";

        return new AiSummary(summary, points);
    }

    private List<Transaction> getTransactionsForAccounts(List<Account> accounts) {
        return accounts.stream()
                .filter(account -> account.getId() != null)
                .flatMap(account -> transactionsRepo.findByAccountId(account.getId()).stream())
                .toList();
    }

    private String transactionId(Transaction transaction) {
        if (transaction.getPlaidTransactionId() != null && !transaction.getPlaidTransactionId().isBlank()) {
            return transaction.getPlaidTransactionId();
        }

        return transaction.getId() != null ? transaction.getId().toString() : "";
    }

    private String transactionStatus(Transaction transaction) {
        if (transaction.isPending()) {
            return "pending";
        }
        return transaction.getType() == TransactionType.INCOME ? "completed" : "settled";
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount != null ? amount.abs() : BigDecimal.ZERO;
    }

    private Money money(BigDecimal amount) {
        return new Money(amount.setScale(2, RoundingMode.HALF_UP), DEFAULT_CURRENCY);
    }

    private String periodLabel(DateRange range) {
        YearMonth fromMonth = YearMonth.from(range.from());
        YearMonth toMonth = YearMonth.from(range.to());
        if (range.from().equals(fromMonth.atDay(1)) && range.to().equals(min(fromMonth.atEndOfMonth(), LocalDate.now()))
                && fromMonth.equals(toMonth)) {
            return fromMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US));
        }
        return range.from().format(SHORT_LABEL_DATE) + " - " + range.to().format(FULL_LABEL_DATE);
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

    private LocalDate min(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private String fallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private enum ReviewQueueSignal {
        PENDING_TRANSACTIONS,
        UNCATEGORIZED_EXPENSES,
        FILES_NEEDING_REVIEW,
        CLEAR
    }

    private record ReviewQueuePresentation(
            String priority,
            String tone,
            String icon,
            String actionLabel,
            String route) {
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
