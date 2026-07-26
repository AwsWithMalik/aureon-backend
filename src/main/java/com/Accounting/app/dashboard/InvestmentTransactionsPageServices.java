package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.dashboard.dto.InvestmentTransactionsPageResponse;
import com.Accounting.app.dashboard.dto.InvestmentTransactionsPageResponse.ActivityPoint;
import com.Accounting.app.dashboard.dto.InvestmentTransactionsPageResponse.Breakdown;
import com.Accounting.app.dashboard.dto.InvestmentTransactionsPageResponse.BreakdownItem;
import com.Accounting.app.dashboard.dto.InvestmentTransactionsPageResponse.Insight;
import com.Accounting.app.dashboard.dto.InvestmentTransactionsPageResponse.Metrics;
import com.Accounting.app.dashboard.dto.InvestmentTransactionsPageResponse.MoneyMetric;
import com.Accounting.app.dashboard.dto.InvestmentTransactionsPageResponse.TransactionRow;
import com.Accounting.app.investments.InvestmentTransaction;
import com.Accounting.app.investments.InvestmentTransactionRepo;
import com.Accounting.app.investments.InvestmentTransactionType;
import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.plaid.PlaidItemRepo;

@Service
public class InvestmentTransactionsPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final DateTimeFormatter FULL_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter MONTH_LABEL_DATE = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
    private static final DateTimeFormatter DAY_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final String[] COLORS = { "#2563eb", "#10b981", "#f97316", "#a855f7", "#64748b", "#eab308" };

    private final PlaidItemRepo plaidItemRepo;
    private final InvestmentTransactionRepo investmentTransactionRepo;
    private final InvestmentStatusPolicy investmentStatusPolicy;

    public InvestmentTransactionsPageServices(
            PlaidItemRepo plaidItemRepo,
            InvestmentTransactionRepo investmentTransactionRepo,
            InvestmentStatusPolicy investmentStatusPolicy) {
        this.plaidItemRepo = plaidItemRepo;
        this.investmentTransactionRepo = investmentTransactionRepo;
        this.investmentStatusPolicy = investmentStatusPolicy;
    }

    @Transactional(readOnly = true)
    public InvestmentTransactionsPageResponse investmentTransactionsPageResponse(
            String email,
            LocalDate from,
            LocalDate to,
            String accountScope) {
        List<PlaidItem> plaidItems = plaidItemRepo.findAllByUser_Email(email);
        List<InvestmentTransaction> scopedTransactions = scopedTransactions(plaidItems, accountScope);
        DateRange range = resolveRange(scopedTransactions, from, to);
        DateRange previousRange = previousRange(range);

        List<InvestmentTransaction> rangeTransactions = transactionsInRange(scopedTransactions, range);
        List<InvestmentTransaction> previousTransactions = transactionsInRange(scopedTransactions, previousRange);
        String currency = resolveCurrency(scopedTransactions);

        BigDecimal totalBuys = amountByTypes(rangeTransactions, InvestmentTransactionType.BUY, InvestmentTransactionType.REINVESTMENT);
        BigDecimal totalSells = amountByTypes(rangeTransactions, InvestmentTransactionType.SELL);
        BigDecimal dividendIncome = amountByTypes(rangeTransactions, InvestmentTransactionType.DIVIDEND);
        BigDecimal feesAndTaxes = amountByTypes(rangeTransactions, InvestmentTransactionType.FEE);
        BigDecimal netActivity = totalSells.add(dividendIncome).subtract(totalBuys).subtract(feesAndTaxes)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal previousBuys = amountByTypes(previousTransactions, InvestmentTransactionType.BUY, InvestmentTransactionType.REINVESTMENT);
        BigDecimal previousSells = amountByTypes(previousTransactions, InvestmentTransactionType.SELL);
        BigDecimal previousDividends = amountByTypes(previousTransactions, InvestmentTransactionType.DIVIDEND);
        BigDecimal previousFees = amountByTypes(previousTransactions, InvestmentTransactionType.FEE);
        BigDecimal previousNet = previousSells.add(previousDividends).subtract(previousBuys).subtract(previousFees)
                .setScale(2, RoundingMode.HALF_UP);

        return new InvestmentTransactionsPageResponse(
                periodLabel(range),
                accountLabel(accountScope),
                new Metrics(
                        metric(totalBuys, previousBuys, currency, previousRange, sparklineForType(scopedTransactions, range.to(), InvestmentTransactionType.BUY, InvestmentTransactionType.REINVESTMENT)),
                        metric(totalSells, previousSells, currency, previousRange, sparklineForType(scopedTransactions, range.to(), InvestmentTransactionType.SELL)),
                        metric(dividendIncome, previousDividends, currency, previousRange, sparklineForType(scopedTransactions, range.to(), InvestmentTransactionType.DIVIDEND)),
                        metric(feesAndTaxes, previousFees, currency, previousRange, sparklineForType(scopedTransactions, range.to(), InvestmentTransactionType.FEE)),
                        metric(netActivity, previousNet, currency, previousRange, netSparkline(scopedTransactions, range.to()))),
                activity(rangeTransactions, range),
                breakdown(rangeTransactions),
                insights(rangeTransactions, totalBuys, totalSells, dividendIncome, feesAndTaxes, netActivity, currency),
                transactions(rangeTransactions, currency));
    }

    private List<InvestmentTransaction> scopedTransactions(List<PlaidItem> plaidItems, String accountScope) {
        return plaidItems.stream()
                .flatMap(plaidItem -> investmentTransactionRepo.findAllByPlaidItem(plaidItem).stream())
                .filter(transaction -> matchesAccountScope(transaction.getAccountId(), accountScope))
                .toList();
    }

    private DateRange resolveRange(List<InvestmentTransaction> transactions, LocalDate from, LocalDate to) {
        if (from != null || to != null) {
            LocalDate normalizedTo = to != null ? to : YearMonth.from(from).atEndOfMonth();
            LocalDate normalizedFrom = from != null ? from : YearMonth.from(normalizedTo).atDay(1);
            return normalizedFrom.isAfter(normalizedTo)
                    ? new DateRange(normalizedTo, normalizedFrom)
                    : new DateRange(normalizedFrom, normalizedTo);
        }

        LocalDate latestDate = transactions.stream()
                .map(InvestmentTransaction::getDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(LocalDate.now());

        YearMonth month = YearMonth.from(latestDate);
        return new DateRange(month.atDay(1), month.atEndOfMonth());
    }

    private DateRange previousRange(DateRange range) {
        long days = range.to().toEpochDay() - range.from().toEpochDay();
        LocalDate previousTo = range.from().minusDays(1);
        return new DateRange(previousTo.minusDays(days), previousTo);
    }

    private List<InvestmentTransaction> transactionsInRange(List<InvestmentTransaction> transactions, DateRange range) {
        return transactions.stream()
                .filter(transaction -> transaction.getDate() != null)
                .filter(transaction -> !transaction.getDate().isBefore(range.from()) && !transaction.getDate().isAfter(range.to()))
                .sorted(Comparator.comparing(InvestmentTransaction::getDate).reversed()
                        .thenComparing(transaction -> fallback(transaction.getSecurityName(), "")))
                .toList();
    }

    private MoneyMetric metric(
            BigDecimal current,
            BigDecimal previous,
            String currency,
            DateRange previousRange,
            List<BigDecimal> sparkline) {
        return new MoneyMetric(
                current,
                currency,
                percentChange(current, previous),
                "vs " + periodLabel(previousRange),
                sparkline);
    }

    private BigDecimal amountByTypes(List<InvestmentTransaction> transactions, InvestmentTransactionType... types) {
        List<InvestmentTransactionType> includedTypes = List.of(types);
        return transactions.stream()
                .filter(transaction -> transaction.getType() != null && includedTypes.contains(transaction.getType()))
                .map(InvestmentTransaction::getAmount)
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<ActivityPoint> activity(List<InvestmentTransaction> transactions, DateRange range) {
        Map<String, BucketAccumulator> buckets = new LinkedHashMap<>();
        if (rangeLengthDays(range) <= 31) {
            for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
                buckets.put(day.format(DAY_LABEL_DATE), new BucketAccumulator());
            }
            for (InvestmentTransaction transaction : transactions) {
                BucketAccumulator bucket = buckets.computeIfAbsent(
                        transaction.getDate().format(DAY_LABEL_DATE),
                        ignored -> new BucketAccumulator());
                applyToBucket(bucket, transaction);
            }
        } else {
            for (int i = 5; i >= 0; i--) {
                YearMonth month = YearMonth.from(range.to()).minusMonths(i);
                buckets.put(month.format(MONTH_LABEL_DATE), new BucketAccumulator());
            }
            for (InvestmentTransaction transaction : transactions) {
                String key = YearMonth.from(transaction.getDate()).format(MONTH_LABEL_DATE);
                BucketAccumulator bucket = buckets.get(key);
                if (bucket != null) {
                    applyToBucket(bucket, transaction);
                }
            }
        }

        return buckets.entrySet().stream()
                .map(entry -> new ActivityPoint(
                        entry.getKey(),
                        scale(entry.getValue().buys),
                        scale(entry.getValue().sells),
                        scale(entry.getValue().dividends),
                        scale(entry.getValue().fees),
                        scale(entry.getValue().net())))
                .toList();
    }

    private void applyToBucket(BucketAccumulator bucket, InvestmentTransaction transaction) {
        BigDecimal amount = safe(transaction.getAmount()).abs();
        InvestmentTransactionType type = transaction.getType();
        if (type == null) {
            return;
        }
        switch (type) {
            case BUY, REINVESTMENT -> bucket.buys = bucket.buys.add(amount);
            case SELL -> bucket.sells = bucket.sells.add(amount);
            case DIVIDEND -> bucket.dividends = bucket.dividends.add(amount);
            case FEE -> bucket.fees = bucket.fees.add(amount);
            default -> {
            }
        }
    }

    private Breakdown breakdown(List<InvestmentTransaction> transactions) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (InvestmentTransaction transaction : transactions) {
            String label = breakdownLabel(transaction.getType());
            counts.merge(label, 1L, Long::sum);
        }

        int total = transactions.size();
        List<Map.Entry<String, Long>> entries = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .toList();
        List<BreakdownItem> items = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Long> entry = entries.get(i);
            items.add(new BreakdownItem(
                    entry.getKey(),
                    total == 0 ? BigDecimal.ZERO.setScale(1) : BigDecimal.valueOf(entry.getValue() * 100.0 / total).setScale(1, RoundingMode.HALF_UP),
                    entry.getValue(),
                    COLORS[i % COLORS.length]));
        }
        return new Breakdown(total, items);
    }

    private List<Insight> insights(
            List<InvestmentTransaction> transactions,
            BigDecimal totalBuys,
            BigDecimal totalSells,
            BigDecimal dividendIncome,
            BigDecimal feesAndTaxes,
            BigDecimal netActivity,
            String currency) {
        List<Insight> insights = new ArrayList<>();
        String dominantType = dominantType(transactions);
        insights.add(new Insight(
                "dominant-activity",
                "Dominant activity",
                dominantType + " was the most common investment activity in the selected period.",
                dominantType,
                transactions.size() + " transactions",
                "info",
                "activity"));

        insights.add(new Insight(
                "net-activity",
                "Net activity",
                netActivity.compareTo(BigDecimal.ZERO) >= 0
                        ? "Sells and dividends outweighed buys and fees."
                        : "Buys and fees outweighed sells and dividends.",
                currency + " " + netActivity.toPlainString(),
                "Selected period",
                netActivity.compareTo(BigDecimal.ZERO) >= 0 ? "success" : "warning",
                "banknote"));

        insights.add(new Insight(
                "income-vs-fees",
                "Income vs fees",
                "Dividend income compared with fees posted in the selected period.",
                currency + " " + dividendIncome.toPlainString(),
                "Fees: " + currency + " " + feesAndTaxes.toPlainString(),
                dividendIncome.compareTo(feesAndTaxes) >= 0 ? "success" : "warning",
                "coins"));

        insights.add(new Insight(
                "buy-sell-balance",
                "Buy vs sell balance",
                "Total buy-side activity against realized sell-side activity.",
                currency + " " + totalBuys.toPlainString(),
                "Sells: " + currency + " " + totalSells.toPlainString(),
                "info",
                "scale"));
        return insights;
    }

    private String dominantType(List<InvestmentTransaction> transactions) {
        return transactions.stream()
                .collect(LinkedHashMap<String, Long>::new,
                        (map, transaction) -> map.merge(breakdownLabel(transaction.getType()), 1L, Long::sum),
                        Map::putAll)
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("No activity");
    }

    private List<TransactionRow> transactions(List<InvestmentTransaction> transactions, String fallbackCurrency) {
        return transactions.stream()
                .map(transaction -> new TransactionRow(
                        transactionId(transaction),
                        transaction.getDate() == null ? "" : transaction.getDate().toString(),
                        fallback(transaction.getSecurityName(), "Investment"),
                        fallback(transaction.getTicker(), ""),
                        fallback(transaction.getAccountName(), "Investment account"),
                        transaction.getType() == null ? "OTHER" : transaction.getType().name(),
                        transaction.getQuantity(),
                        transaction.getPrice(),
                        scale(safe(transaction.getAmount()).abs()),
                        fallback(transaction.getCurrency(), fallbackCurrency),
                        investmentStatusPolicy.determineInvestmentTransactionStatus(transaction),
                        logoText(fallback(transaction.getTicker(), transaction.getSecurityName())),
                        null))
                .toList();
    }

    private List<BigDecimal> sparklineForType(
            List<InvestmentTransaction> transactions,
            LocalDate endDate,
            InvestmentTransactionType... types) {
        List<BigDecimal> points = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = YearMonth.from(endDate).minusMonths(i);
            List<InvestmentTransaction> monthTransactions = transactions.stream()
                    .filter(transaction -> transaction.getDate() != null)
                    .filter(transaction -> YearMonth.from(transaction.getDate()).equals(month))
                    .toList();
            points.add(amountByTypes(monthTransactions, types));
        }
        return points;
    }

    private List<BigDecimal> netSparkline(List<InvestmentTransaction> transactions, LocalDate endDate) {
        List<BigDecimal> points = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = YearMonth.from(endDate).minusMonths(i);
            List<InvestmentTransaction> monthTransactions = transactions.stream()
                    .filter(transaction -> transaction.getDate() != null)
                    .filter(transaction -> YearMonth.from(transaction.getDate()).equals(month))
                    .toList();
            BigDecimal buys = amountByTypes(monthTransactions, InvestmentTransactionType.BUY, InvestmentTransactionType.REINVESTMENT);
            BigDecimal sells = amountByTypes(monthTransactions, InvestmentTransactionType.SELL);
            BigDecimal dividends = amountByTypes(monthTransactions, InvestmentTransactionType.DIVIDEND);
            BigDecimal fees = amountByTypes(monthTransactions, InvestmentTransactionType.FEE);
            points.add(scale(sells.add(dividends).subtract(buys).subtract(fees)));
        }
        return points;
    }

    private boolean matchesAccountScope(String accountId, String accountScope) {
        return accountScope == null
                || accountScope.isBlank()
                || "all".equalsIgnoreCase(accountScope)
                || Objects.equals(accountId, accountScope);
    }

    private String resolveCurrency(List<InvestmentTransaction> transactions) {
        return transactions.stream()
                .map(InvestmentTransaction::getCurrency)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(DEFAULT_CURRENCY);
    }

    private long rangeLengthDays(DateRange range) {
        return Math.max(1, range.to().toEpochDay() - range.from().toEpochDay() + 1);
    }

    private String breakdownLabel(InvestmentTransactionType type) {
        if (type == null) {
            return "Other";
        }
        return switch (type) {
            case BUY -> "Buy";
            case SELL -> "Sell";
            case DIVIDEND -> "Dividend";
            case REINVESTMENT -> "Reinvestment";
            case FEE -> "Fee";
            case CASH -> "Cash";
            case TRANSFER -> "Transfer";
            case OTHER -> "Other";
        };
    }

    private String transactionId(InvestmentTransaction transaction) {
        if (transaction.getPlaidInvestmentTransactionId() != null && !transaction.getPlaidInvestmentTransactionId().isBlank()) {
            return transaction.getPlaidInvestmentTransactionId();
        }
        return transaction.getId() == null ? "" : "investment-transaction-" + transaction.getId();
    }

    private String periodLabel(DateRange range) {
        return range.from().format(SHORT_LABEL_DATE) + " - " + range.to().format(FULL_LABEL_DATE);
    }

    private String accountLabel(String accountScope) {
        return accountScope == null || accountScope.isBlank() || "all".equalsIgnoreCase(accountScope)
                ? "All accounts"
                : "Selected account";
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

    private BigDecimal scale(BigDecimal value) {
        return safe(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String logoText(String value) {
        String cleaned = fallback(value, "inv").replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isBlank()) {
            return "inv";
        }
        return cleaned.length() <= 4
                ? cleaned.toLowerCase(Locale.US)
                : cleaned.substring(0, 4).toLowerCase(Locale.US);
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }

    private static final class BucketAccumulator {
        private BigDecimal buys = BigDecimal.ZERO;
        private BigDecimal sells = BigDecimal.ZERO;
        private BigDecimal dividends = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;

        private BigDecimal net() {
            return sells.add(dividends).subtract(buys).subtract(fees);
        }
    }
}
