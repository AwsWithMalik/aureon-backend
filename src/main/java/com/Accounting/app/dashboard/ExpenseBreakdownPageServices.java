package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.AiSummary;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.CategoryAmount;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.ExpenseDetail;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.Highlight;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.LargestCategory;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.Metrics;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.MonthOverMonthChange;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.MonthlyTrendPeriod;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.NeedsReview;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.QuickFilter;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.TotalExpenses;
import com.Accounting.app.dashboard.dto.ExpenseBreakdownPageResponse.TrendCategory;
import com.Accounting.app.files.FileRepo;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.transactions.TransactionType;
import com.Accounting.app.transactions.TransactionsRepo;

@Service
public class ExpenseBreakdownPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
    private static final String[] CATEGORY_COLORS = {
            "#2563eb", "#0ea5e9", "#10b981", "#f97316", "#a16207", "#64748b", "#ef4444"
    };

    private final AccountRepo accountRepo;
    private final TransactionsRepo transactionsRepo;
    private final FileRepo fileRepo;
    private final MerchantLogoUrlService merchantLogoUrlService;

    public ExpenseBreakdownPageServices(
            AccountRepo accountRepo,
            TransactionsRepo transactionsRepo,
            FileRepo fileRepo,
            MerchantLogoUrlService merchantLogoUrlService) {
        this.accountRepo = accountRepo;
        this.transactionsRepo = transactionsRepo;
        this.fileRepo = fileRepo;
        this.merchantLogoUrlService = merchantLogoUrlService;
    }

    public ExpenseBreakdownPageResponse expenseBreakdownPageResponse(
            String email,
            YearMonth month,
            String accountScope,
            YearMonth compareTo) {
        List<Account> scopedAccounts = scopedAccounts(accountRepo.findAllByEmail(email), accountScope);
        YearMonth selectedMonth = resolveSelectedMonth(scopedAccounts, month);
        YearMonth comparisonMonth = compareTo == null ? selectedMonth.minusMonths(1) : compareTo;
        List<Transaction> currentExpenses = expenseTransactions(scopedAccounts, selectedMonth);
        List<Transaction> previousExpenses = expenseTransactions(scopedAccounts, comparisonMonth);

        List<CategoryAmount> categoryBreakdown = categoryBreakdown(currentExpenses);
        LargestCategory largestCategory = largestCategory(categoryBreakdown);
        List<ExpenseDetail> expenseDetails = expenseDetails(currentExpenses, email);
        ExpenseMetricsSummary metricsSummary = summarizeMetrics(expenseDetails, previousExpenses, largestCategory);

        return new ExpenseBreakdownPageResponse(
                selectedMonth.format(MONTH_LABEL),
                accountLabel(accountScope),
                "vs " + comparisonMonth.format(MONTH_LABEL),
                metrics(metricsSummary),
                categoryBreakdown,
                categoryBreakdown,
                monthlyTrend(scopedAccounts, selectedMonth),
                highlights(metricsSummary),
                aiSummary(metricsSummary),
                expenseDetails,
                quickFilters(categoryBreakdown, metricsSummary.needsReviewCount()));
    }

    private YearMonth resolveSelectedMonth(List<Account> accounts, YearMonth requestedMonth) {
        if (requestedMonth != null) {
            return requestedMonth;
        }

        return accounts.stream()
                .filter(account -> account.getId() != null)
                .flatMap(account -> transactionsRepo.findByAccountId(account.getId()).stream())
                .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
                .filter(transaction -> transaction.getTimestamp() != null)
                .map(transaction -> YearMonth.from(transaction.getTimestamp()))
                .max(Comparator.naturalOrder())
                .orElse(YearMonth.now());
    }

    private List<Account> scopedAccounts(List<Account> accounts, String accountScope) {
        if (accountScope == null || accountScope.isBlank() || "all".equalsIgnoreCase(accountScope)) {
            return accounts;
        }

        return accounts.stream()
                .filter(account -> accountScope.equals(stableAccountId(account))
                        || accountScope.equals(account.getAccountId())
                        || accountScope.equals(account.getPlaidAccountId())
                        || accountScope.equals(String.valueOf(account.getId())))
                .toList();
    }

    private List<Transaction> expenseTransactions(List<Account> accounts, YearMonth month) {
        return accounts.stream()
                .filter(account -> account.getId() != null)
                .flatMap(account -> transactionsRepo.findByAccountId(account.getId()).stream())
                .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
                .filter(transaction -> transaction.getTimestamp() != null)
                .filter(transaction -> YearMonth.from(transaction.getTimestamp()).equals(month))
                .sorted(Comparator.comparing(Transaction::getTimestamp, Comparator.reverseOrder()))
                .toList();
    }

    private List<CategoryAmount> categoryBreakdown(List<Transaction> expenses) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();

        for (Transaction transaction : expenses) {
            totals.merge(category(transaction), safeAmount(transaction.getAmount()).abs(), BigDecimal::add);
        }

        BigDecimal overallTotal = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map.Entry<String, BigDecimal>> sorted = totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .toList();

        List<CategoryAmount> breakdown = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, BigDecimal> entry = sorted.get(i);
            breakdown.add(new CategoryAmount(
                    entry.getKey(),
                    entry.getValue(),
                    percent(entry.getValue(), overallTotal),
                    CATEGORY_COLORS[i % CATEGORY_COLORS.length]));
        }
        return breakdown;
    }

    private LargestCategory largestCategory(List<CategoryAmount> categoryBreakdown) {
        if (categoryBreakdown.isEmpty()) {
            return new LargestCategory("Uncategorized", BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(1));
        }

        CategoryAmount largest = categoryBreakdown.get(0);
        return new LargestCategory(largest.label(), largest.amount(), largest.percent());
    }

    private List<ExpenseDetail> expenseDetails(List<Transaction> expenses, String email) {
        return expenses.stream()
                .map(transaction -> toExpenseDetail(transaction, email))
                .toList();
    }

    private ExpenseDetail toExpenseDetail(Transaction transaction, String email) {
        long attachmentCount = transaction.getId() == null
                ? 0
                : fileRepo.countByRelatedTransaction_TransactionIdAndUser_Email(transaction.getId(), email);
        String status = determineExpenseStatus(transaction, attachmentCount);

        return new ExpenseDetail(
                stableTransactionId(transaction),
                transaction.getTimestamp() == null ? "" : transaction.getTimestamp().toLocalDate().toString(),
                fallback(transaction.getMerchantName(), fallback(transaction.getDescription(), "Expense")),
                category(transaction),
                transaction.getAccount() == null ? "Account" : fallback(transaction.getAccount().getAccountName(), "Account"),
                safeAmount(transaction.getAmount()).abs(),
                DEFAULT_CURRENCY,
                status,
                merchantLogoUrlService.toClientLogoUrl(metadataValue(transaction, "logoUrl")),
                logoText(fallback(transaction.getMerchantName(), transaction.getDescription())));
    }

    private String determineExpenseStatus(Transaction transaction, long attachmentCount) {
        if (attachmentCount > 0) {
            return "matched_receipt";
        }

        String category = transaction.getDisplayCategory();
        if (transaction.isPending()
                || category == null
                || category.isBlank()
                || "uncategorized".equalsIgnoreCase(category)
                || missingMerchantAndDescription(transaction)
                || safeAmount(transaction.getAmount()).compareTo(BigDecimal.ZERO) == 0) {
            return "needs_review";
        }

        return "categorized";
    }

    private boolean missingMerchantAndDescription(Transaction transaction) {
        return blank(transaction.getMerchantName()) && blank(transaction.getDescription());
    }

    private List<MonthlyTrendPeriod> monthlyTrend(List<Account> accounts, YearMonth selectedMonth) {
        List<YearMonth> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            months.add(selectedMonth.minusMonths(i));
        }

        List<String> topCategories = monthlyTopCategories(accounts, selectedMonth);
        List<MonthlyTrendPeriod> trend = new ArrayList<>();

        for (YearMonth month : months) {
            List<Transaction> expenses = expenseTransactions(accounts, month);
            Map<String, BigDecimal> totals = new LinkedHashMap<>();
            for (String category : topCategories) {
                totals.put(category, BigDecimal.ZERO);
            }
            for (Transaction transaction : expenses) {
                String category = category(transaction);
                if (!totals.containsKey(category)) {
                    continue;
                }
                totals.merge(category, safeAmount(transaction.getAmount()).abs(), BigDecimal::add);
            }

            List<TrendCategory> categories = new ArrayList<>();
            for (int i = 0; i < topCategories.size(); i++) {
                String category = topCategories.get(i);
                categories.add(new TrendCategory(
                        category,
                        totals.getOrDefault(category, BigDecimal.ZERO),
                        CATEGORY_COLORS[i % CATEGORY_COLORS.length]));
            }

            BigDecimal total = expenses.stream()
                    .map(Transaction::getAmount)
                    .filter(amount -> amount != null)
                    .map(BigDecimal::abs)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            trend.add(new MonthlyTrendPeriod(month.format(MONTH_LABEL), categories, total));
        }

        return trend;
    }

    private List<String> monthlyTopCategories(List<Account> accounts, YearMonth selectedMonth) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Transaction transaction : expenseTransactions(accounts, selectedMonth)) {
            totals.merge(category(transaction), safeAmount(transaction.getAmount()).abs(), BigDecimal::add);
        }

        List<String> topCategories = totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(4)
                .map(Map.Entry::getKey)
                .toList();

        if (!topCategories.isEmpty()) {
            return topCategories;
        }

        return List.of("Uncategorized");
    }

    private List<Highlight> highlights(ExpenseMetricsSummary metricsSummary) {
        List<Highlight> highlights = new ArrayList<>();
        BigDecimal change = metricsSummary.totalExpenses().changePercent();
        LargestCategory largestCategory = metricsSummary.largestCategory();

        highlights.add(new Highlight(
                "largest-category",
                "Largest expense category",
                largestCategory.label() + " accounts for " + largestCategory.percentOfTotal().setScale(1, RoundingMode.HALF_UP).toPlainString() + "% of total expenses.",
                largestCategory.percentOfTotal().compareTo(BigDecimal.valueOf(35)) >= 0 ? "warning" : "info"));

        highlights.add(new Highlight(
                "month-over-month",
                "Month-over-month change",
                "Expenses changed by " + change.abs().setScale(1, RoundingMode.HALF_UP).toPlainString() + "% compared with the prior month.",
                change.compareTo(BigDecimal.ZERO) > 0 ? "warning" : "success"));

        if (metricsSummary.needsReviewCount() > 0) {
            highlights.add(new Highlight(
                    "needs-review",
                    "Items need review",
                    metricsSummary.needsReviewCount() + " expense items still need categorization or confirmation.",
                    "warning"));
        } else {
            highlights.add(new Highlight(
                    "categorized",
                    "All visible expenses are categorized",
                    "No expense items are waiting for review in the selected month.",
                    "success"));
        }

        return highlights;
    }

    private AiSummary aiSummary(ExpenseMetricsSummary metricsSummary) {
        BigDecimal change = metricsSummary.totalExpenses().changePercent();
        LargestCategory largestCategory = metricsSummary.largestCategory();
        return new AiSummary(
                largestCategory.label() + " is the largest spend category, and total expenses are "
                        + (change.compareTo(BigDecimal.ZERO) >= 0 ? "up " : "down ")
                        + change.abs().setScale(1, RoundingMode.HALF_UP).toPlainString()
                        + "% versus the comparison month.",
                "AI-powered");
    }

    private Metrics metrics(ExpenseMetricsSummary summary) {
        return new Metrics(
                summary.totalExpenses(),
                summary.largestCategory(),
                new NeedsReview(summary.needsReviewCount(), "Expense items"),
                new MonthOverMonthChange(summary.totalExpenses().changePercent()));
    }

    private ExpenseMetricsSummary summarizeMetrics(
            List<ExpenseDetail> currentExpenseDetails,
            List<Transaction> previousExpenses,
            LargestCategory largestCategory) {
        BigDecimal totalExpenses = currentExpenseDetails.stream()
                .map(ExpenseDetail::amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousTotalExpenses = previousExpenses.stream()
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal changePercent = percentChange(totalExpenses, previousTotalExpenses);
        long needsReviewCount = currentExpenseDetails.stream()
                .filter(detail -> "needs_review".equals(detail.status()))
                .count();

        return new ExpenseMetricsSummary(
                new TotalExpenses(totalExpenses, DEFAULT_CURRENCY, changePercent),
                largestCategory,
                needsReviewCount);
    }

    private List<QuickFilter> quickFilters(List<CategoryAmount> categoryBreakdown, long needsReviewCount) {
        List<QuickFilter> filters = new ArrayList<>();
        for (CategoryAmount item : categoryBreakdown.stream().limit(5).toList()) {
            filters.add(new QuickFilter(
                    toId(item.label()),
                    item.label(),
                    iconKey(item.label()),
                    Boolean.FALSE));
        }

        if (needsReviewCount > 0) {
            filters.add(new QuickFilter("needs-review", "Needs review", "alert-triangle", Boolean.FALSE));
        }

        return filters;
    }

    private String category(Transaction transaction) {
        String category = transaction.getDisplayCategory();
        return category == null || category.isBlank() ? "Uncategorized" : category;
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(1);
        }

        return amount.multiply(BigDecimal.valueOf(100))
                .divide(total.abs(), 1, RoundingMode.HALF_UP);
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

    private String metadataValue(Transaction transaction, String key) {
        if (transaction.getMetadata() == null) {
            return null;
        }
        String prefix = key + "=";
        return transaction.getMetadata().stream()
                .filter(value -> value != null && value.startsWith(prefix))
                .map(value -> value.substring(prefix.length()))
                .findFirst()
                .orElse(null);
    }

    private String logoText(String value) {
        if (value == null || value.isBlank()) {
            return "exp";
        }

        String cleaned = value.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isBlank()) {
            return "exp";
        }

        return cleaned.length() <= 3
                ? cleaned.toLowerCase(Locale.US)
                : cleaned.substring(0, 3).toLowerCase(Locale.US);
    }

    private String stableTransactionId(Transaction transaction) {
        if (transaction.getPlaidTransactionId() != null && !transaction.getPlaidTransactionId().isBlank()) {
            return transaction.getPlaidTransactionId();
        }
        return transaction.getId() == null ? "" : "transaction-" + transaction.getId();
    }

    private String stableAccountId(Account account) {
        if (account.getAccountId() != null && !account.getAccountId().isBlank()) {
            return account.getAccountId();
        }
        if (account.getPlaidAccountId() != null && !account.getPlaidAccountId().isBlank()) {
            return account.getPlaidAccountId();
        }
        return account.getId() == null ? "" : "account-" + account.getId();
    }

    private String accountLabel(String accountScope) {
        return accountScope == null || accountScope.isBlank() || "all".equalsIgnoreCase(accountScope)
                ? "All accounts"
                : "Selected account";
    }

    private String toId(String value) {
        return value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String iconKey(String label) {
        String normalized = label.toLowerCase(Locale.US);
        if (normalized.contains("software")) {
            return "monitor";
        }
        if (normalized.contains("meal") || normalized.contains("food")) {
            return "utensils";
        }
        if (normalized.contains("transport") || normalized.contains("gas")) {
            return "car";
        }
        if (normalized.contains("office")) {
            return "briefcase";
        }
        return "circle";
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ExpenseMetricsSummary(
            TotalExpenses totalExpenses,
            LargestCategory largestCategory,
            long needsReviewCount) {
    }
}
