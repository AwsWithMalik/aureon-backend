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
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.dashboard.dto.DividendsPageResponse;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.AverageDividendYield;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.Breakdown;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.BreakdownItem;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.BreakdownTotal;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.HighestDividendPayer;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.HistoryItem;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.IncomeTrendPoint;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.Insight;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.Metrics;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.MoneyMetric;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.UpcomingPayouts;
import com.Accounting.app.dashboard.dto.DividendsPageResponse.YieldSnapshotItem;
import com.Accounting.app.investments.InvestmentHolding;
import com.Accounting.app.investments.InvestmentHoldingRepo;
import com.Accounting.app.investments.MarketQuoteService;
import com.Accounting.app.investments.InvestmentSecurity;
import com.Accounting.app.investments.InvestmentSecurityRepo;
import com.Accounting.app.investments.InvestmentTransaction;
import com.Accounting.app.investments.InvestmentTransactionRepo;
import com.Accounting.app.investments.InvestmentTransactionType;
import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.plaid.PlaidItemRepo;

@Service
public class DividendsPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final DateTimeFormatter FULL_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
    private static final String[] COLORS = { "#2563eb", "#10b981", "#f97316", "#a855f7", "#64748b", "#eab308" };

    private final PlaidItemRepo plaidItemRepo;
    private final InvestmentTransactionRepo investmentTransactionRepo;
    private final InvestmentHoldingRepo investmentHoldingRepo;
    private final InvestmentSecurityRepo investmentSecurityRepo;
    private final InvestmentStatusPolicy investmentStatusPolicy;
    private final MarketQuoteService marketQuoteService;

    public DividendsPageServices(
            PlaidItemRepo plaidItemRepo,
            InvestmentTransactionRepo investmentTransactionRepo,
            InvestmentHoldingRepo investmentHoldingRepo,
            InvestmentSecurityRepo investmentSecurityRepo,
            InvestmentStatusPolicy investmentStatusPolicy,
            MarketQuoteService marketQuoteService) {
        this.plaidItemRepo = plaidItemRepo;
        this.investmentTransactionRepo = investmentTransactionRepo;
        this.investmentHoldingRepo = investmentHoldingRepo;
        this.investmentSecurityRepo = investmentSecurityRepo;
        this.investmentStatusPolicy = investmentStatusPolicy;
        this.marketQuoteService = marketQuoteService;
    }

    @Transactional(readOnly = true)
    public DividendsPageResponse dividendsPageResponse(String email, LocalDate from, LocalDate to, String accountScope) {
        List<PlaidItem> plaidItems = plaidItemRepo.findAllByUser_Email(email);
        List<HoldingContext> holdingContexts = scopedHoldings(plaidItems, accountScope);
        marketQuoteService.refreshQuotes(holdingContexts.stream().map(HoldingContext::security).filter(Objects::nonNull).toList());
        List<InvestmentTransaction> scopedDividendTransactions = scopedDividendTransactions(plaidItems, accountScope);
        DateRange range = resolveRange(holdingContexts, scopedDividendTransactions, from, to);
        DateRange previousRange = previousRange(range);

        List<InvestmentTransaction> rangeTransactions = transactionsInRange(scopedDividendTransactions, range);
        List<InvestmentTransaction> previousTransactions = transactionsInRange(scopedDividendTransactions, previousRange);
        String currency = resolveCurrency(holdingContexts, scopedDividendTransactions);

        BigDecimal totalDividendIncome = totalAmount(rangeTransactions);
        BigDecimal previousDividendIncome = totalAmount(previousTransactions);
        BigDecimal ytdDividends = totalAmount(scopedDividendTransactions.stream()
                .filter(transaction -> transaction.getDate() != null)
                .filter(transaction -> !transaction.getDate().isBefore(range.to().withDayOfYear(1)) && !transaction.getDate().isAfter(range.to()))
                .toList());
        BigDecimal previousYtdDividends = totalAmount(scopedDividendTransactions.stream()
                .filter(transaction -> transaction.getDate() != null)
                .filter(transaction -> !transaction.getDate().isBefore(range.to().minusYears(1).withDayOfYear(1))
                        && !transaction.getDate().isAfter(range.to().minusYears(1)))
                .toList());

        YieldResult currentYield = averageYield(rangeTransactions, holdingContexts);
        YieldResult previousYield = averageYield(previousTransactions, holdingContexts);
        HighestDividendPayer highestPayer = highestPayer(rangeTransactions, currency);

        List<HistoryItem> history = history(rangeTransactions, holdingContexts, currency);
        List<BreakdownItem> breakdownItems = breakdownItems(rangeTransactions, currency);

        return new DividendsPageResponse(
                periodLabel(range),
                accountLabel(accountScope),
                new Metrics(
                        new MoneyMetric(
                                totalDividendIncome,
                                currency,
                                percentChange(totalDividendIncome, previousDividendIncome),
                                "vs " + periodLabel(previousRange),
                                incomeSparkline(scopedDividendTransactions, range.to(), 6)),
                        new MoneyMetric(
                                ytdDividends,
                                currency,
                                percentChange(ytdDividends, previousYtdDividends),
                                "YTD",
                                incomeSparkline(scopedDividendTransactions, range.to(), 6)),
                        new UpcomingPayouts(
                                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                                currency,
                                "Next 30 days",
                                List.of(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))),
                        new AverageDividendYield(
                                currentYield.percent(),
                                currentYield.percent().subtract(previousYield.percent()).setScale(2, RoundingMode.HALF_UP),
                                "vs " + periodLabel(previousRange),
                                yieldSparkline(holdingContexts)),
                        highestPayer),
                incomeTrend(scopedDividendTransactions, range.to()),
                List.of(),
                new Breakdown(
                        new BreakdownTotal(totalDividendIncome, currency, "Selected period"),
                        breakdownItems),
                history,
                insights(totalDividendIncome, ytdDividends, highestPayer, currentYield, history.size(), currency),
                yieldSnapshot(holdingContexts));
    }

    private List<HoldingContext> scopedHoldings(List<PlaidItem> plaidItems, String accountScope) {
        return plaidItems.stream()
                .flatMap(plaidItem -> investmentHoldingRepo.findAllByPlaidItem(plaidItem).stream()
                        .filter(holding -> matchesAccountScope(holding.getAccountId(), accountScope))
                        .map(holding -> new HoldingContext(plaidItem, holding, security(holding.getSecurityId()))))
                .toList();
    }

    private List<InvestmentTransaction> scopedDividendTransactions(List<PlaidItem> plaidItems, String accountScope) {
        return plaidItems.stream()
                .flatMap(plaidItem -> investmentTransactionRepo.findAllByPlaidItem(plaidItem).stream())
                .filter(transaction -> transaction.getType() == InvestmentTransactionType.DIVIDEND)
                .filter(transaction -> matchesAccountScope(transaction.getAccountId(), accountScope))
                .toList();
    }

    private DateRange resolveRange(
            List<HoldingContext> holdingContexts,
            List<InvestmentTransaction> transactions,
            LocalDate from,
            LocalDate to) {
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
                .orElseGet(() -> holdingContexts.stream()
                        .map(context -> context.holding().getSyncedAt())
                        .filter(Objects::nonNull)
                        .map(LocalDateTime::toLocalDate)
                        .max(Comparator.naturalOrder())
                        .orElse(LocalDate.now()));

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
                .sorted(Comparator.comparing(InvestmentTransaction::getDate).reversed())
                .toList();
    }

    private BigDecimal totalAmount(List<InvestmentTransaction> transactions) {
        return transactions.stream()
                .map(InvestmentTransaction::getAmount)
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private YieldResult averageYield(List<InvestmentTransaction> transactions, List<HoldingContext> holdingContexts) {
        if (transactions.isEmpty()) {
            return new YieldResult(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        }

        BigDecimal totalYield = BigDecimal.ZERO;
        int count = 0;
        for (InvestmentTransaction transaction : transactions) {
            BigDecimal yield = yieldPercent(transaction, holdingContexts);
            totalYield = totalYield.add(yield);
            count++;
        }

        if (count == 0) {
            return new YieldResult(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        }

        return new YieldResult(
                totalYield.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(count));
    }

    private HighestDividendPayer highestPayer(List<InvestmentTransaction> transactions, String currency) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (InvestmentTransaction transaction : transactions) {
            totals.merge(
                    fallback(transaction.getSecurityName(), "Dividend"),
                    safe(transaction.getAmount()).abs(),
                    BigDecimal::add);
        }

        Map.Entry<String, BigDecimal> top = totals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        if (top == null) {
            return new HighestDividendPayer("No dividends", BigDecimal.ZERO, currency, "Top payer", List.of(BigDecimal.ZERO));
        }

        return new HighestDividendPayer(
                top.getKey(),
                top.getValue().setScale(2, RoundingMode.HALF_UP),
                currency,
                "Top payer",
                sparkline(top.getValue().multiply(BigDecimal.valueOf(0.7)), top.getValue()));
    }

    private List<IncomeTrendPoint> incomeTrend(List<InvestmentTransaction> transactions, LocalDate endDate) {
        List<IncomeTrendPoint> trend = new ArrayList<>();
        List<BigDecimal> running = new ArrayList<>();

        for (int i = 5; i >= 0; i--) {
            YearMonth month = YearMonth.from(endDate).minusMonths(i);
            BigDecimal income = totalAmount(transactions.stream()
                    .filter(transaction -> transaction.getDate() != null)
                    .filter(transaction -> YearMonth.from(transaction.getDate()).equals(month))
                    .toList());
            running.add(income);
            BigDecimal average = running.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(running.size()), 2, RoundingMode.HALF_UP);
            trend.add(new IncomeTrendPoint(month.format(MONTH_LABEL), income, average));
        }

        return trend;
    }

    private List<BreakdownItem> breakdownItems(List<InvestmentTransaction> transactions, String currency) {
        BigDecimal total = totalAmount(transactions);
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (InvestmentTransaction transaction : transactions) {
            totals.merge(
                    fallback(transaction.getSecurityName(), "Dividend"),
                    safe(transaction.getAmount()).abs(),
                    BigDecimal::add);
        }

        List<Map.Entry<String, BigDecimal>> entries = totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .toList();
        List<BreakdownItem> items = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, BigDecimal> entry = entries.get(i);
            items.add(new BreakdownItem(
                    entry.getKey(),
                    percent(entry.getValue(), total),
                    entry.getValue().setScale(2, RoundingMode.HALF_UP),
                    currency,
                    COLORS[i % COLORS.length]));
        }
        return items;
    }

    private List<HistoryItem> history(List<InvestmentTransaction> transactions, List<HoldingContext> holdingContexts, String fallbackCurrency) {
        return transactions.stream()
                .map(transaction -> historyItem(transaction, holdingContexts, fallbackCurrency))
                .toList();
    }

    private HistoryItem historyItem(InvestmentTransaction transaction, List<HoldingContext> holdingContexts, String fallbackCurrency) {
        BigDecimal totalAmount = safe(transaction.getAmount()).abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal shares = transaction.getQuantity();
        BigDecimal dividendPerShare = (shares == null || shares.compareTo(BigDecimal.ZERO) == 0)
                ? null
                : totalAmount.divide(shares, 4, RoundingMode.HALF_UP);

        return new HistoryItem(
                holdingId(transaction),
                transaction.getDate() == null ? "" : transaction.getDate().toString(),
                fallback(transaction.getSecurityName(), "Dividend"),
                fallback(transaction.getAccountName(), "Investment account"),
                transaction.getType() == null ? "DIVIDEND" : transaction.getType().name(),
                dividendPerShare,
                shares,
                totalAmount,
                fallback(transaction.getCurrency(), fallbackCurrency),
                yieldPercent(transaction, holdingContexts),
                investmentStatusPolicy.determineInvestmentTransactionStatus(transaction),
                logoText(fallback(transaction.getTicker(), transaction.getSecurityName())),
                null);
    }

    private BigDecimal yieldPercent(InvestmentTransaction transaction, List<HoldingContext> holdingContexts) {
        BigDecimal totalAmount = safe(transaction.getAmount()).abs();
        BigDecimal quantity = transaction.getQuantity();
        BigDecimal price = safe(transaction.getPrice());
        if (price.compareTo(BigDecimal.ZERO) == 0) {
            price = currentPrice(transaction, holdingContexts);
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0 || price.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal dividendPerShare = totalAmount.divide(quantity, 6, RoundingMode.HALF_UP);
        return dividendPerShare.multiply(BigDecimal.valueOf(100))
                .divide(price, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal currentPrice(InvestmentTransaction transaction, List<HoldingContext> holdingContexts) {
        return holdingContexts.stream()
                .filter(context -> Objects.equals(context.holding().getAccountId(), transaction.getAccountId()))
                .filter(context -> Objects.equals(context.holding().getSecurityId(), transaction.getSecurityId()))
                .map(context -> marketQuoteService.currentPrice(context.holding(), context.security()))
                .filter(price -> price.compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElseGet(() -> {
                    InvestmentSecurity security = security(transaction.getSecurityId());
                    return security == null ? BigDecimal.ZERO : marketQuoteService.currentPrice(null, security);
                });
    }

    private List<Insight> insights(
            BigDecimal totalDividendIncome,
            BigDecimal ytdDividends,
            HighestDividendPayer highestPayer,
            YieldResult yieldResult,
            int historyCount,
            String currency) {
        List<Insight> insights = new ArrayList<>();
        insights.add(new Insight(
                "selected-period-income",
                "Dividend income",
                "Total dividends received in the selected period.",
                currency + " " + totalDividendIncome.toPlainString(),
                null,
                "info",
                "coins"));
        insights.add(new Insight(
                "ytd-income",
                "YTD dividends",
                "Dividends received since the start of the current year.",
                currency + " " + ytdDividends.toPlainString(),
                null,
                "success",
                "calendar"));
        insights.add(new Insight(
                "highest-payer",
                "Highest payer",
                highestPayer.label() + " contributed the most dividend income in the selected period.",
                currency + " " + highestPayer.amount().toPlainString(),
                highestPayer.detailLabel(),
                "info",
                "badge-dollar-sign"));
        insights.add(new Insight(
                "average-yield",
                "Average dividend yield",
                "Average implied dividend yield across posted dividend events.",
                yieldResult.percent().toPlainString() + "%",
                historyCount + " payouts",
                "info",
                "percent"));
        return insights;
    }

    private List<YieldSnapshotItem> yieldSnapshot(List<HoldingContext> holdingContexts) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal portfolioTotal = holdingContexts.stream()
                .map(this::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (HoldingContext context : holdingContexts) {
            String label = yieldLabel(context);
            totals.merge(label, marketValue(context), BigDecimal::add);
        }

        List<Map.Entry<String, BigDecimal>> entries = totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .toList();
        List<YieldSnapshotItem> items = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, BigDecimal> entry = entries.get(i);
            items.add(new YieldSnapshotItem(
                    entry.getKey(),
                    entry.getValue().setScale(2, RoundingMode.HALF_UP),
                    percent(entry.getValue(), portfolioTotal),
                    COLORS[i % COLORS.length]));
        }
        return items;
    }

    private String yieldLabel(HoldingContext context) {
        InvestmentSecurity security = context.security();
        if (security == null) {
            return "Other";
        }
        String value = String.join(" ",
                fallback(security.getType(), ""),
                fallback(security.getSubtype(), ""),
                fallback(security.getName(), ""))
                .toLowerCase(Locale.US);
        if (value.contains("etf")) {
            return "ETFs";
        }
        if (value.contains("fund")) {
            return "Funds";
        }
        if (value.contains("cash")) {
            return "Cash";
        }
        return "Equities";
    }

    private List<BigDecimal> incomeSparkline(List<InvestmentTransaction> transactions, LocalDate endDate, int months) {
        List<BigDecimal> points = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth month = YearMonth.from(endDate).minusMonths(i);
            points.add(totalAmount(transactions.stream()
                    .filter(transaction -> transaction.getDate() != null)
                    .filter(transaction -> YearMonth.from(transaction.getDate()).equals(month))
                    .toList()));
        }
        return points;
    }

    private List<BigDecimal> yieldSparkline(List<HoldingContext> holdingContexts) {
        BigDecimal value = yieldSnapshot(holdingContexts).stream()
                .map(YieldSnapshotItem::percent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return List.of(
                value.multiply(BigDecimal.valueOf(0.7)).setScale(2, RoundingMode.HALF_UP),
                value.multiply(BigDecimal.valueOf(0.8)).setScale(2, RoundingMode.HALF_UP),
                value.multiply(BigDecimal.valueOf(0.75)).setScale(2, RoundingMode.HALF_UP),
                value.multiply(BigDecimal.valueOf(0.9)).setScale(2, RoundingMode.HALF_UP),
                value.multiply(BigDecimal.valueOf(0.85)).setScale(2, RoundingMode.HALF_UP),
                value.setScale(2, RoundingMode.HALF_UP));
    }

    private boolean matchesAccountScope(String accountId, String accountScope) {
        return accountScope == null
                || accountScope.isBlank()
                || "all".equalsIgnoreCase(accountScope)
                || Objects.equals(accountId, accountScope);
    }

    private InvestmentSecurity security(String securityId) {
        if (securityId == null || securityId.isBlank()) {
            return null;
        }
        return investmentSecurityRepo.findBySecurityId(securityId).orElse(null);
    }

    private String resolveCurrency(List<HoldingContext> holdings, List<InvestmentTransaction> transactions) {
        return holdings.stream()
                .map(context -> context.holding().getCurrency())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> transactions.stream()
                        .map(InvestmentTransaction::getCurrency)
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst()
                        .orElse(DEFAULT_CURRENCY));
    }

    private String periodLabel(DateRange range) {
        return range.from().format(SHORT_LABEL_DATE) + " - " + range.to().format(FULL_LABEL_DATE);
    }

    private String accountLabel(String accountScope) {
        return accountScope == null || accountScope.isBlank() || "all".equalsIgnoreCase(accountScope)
                ? "All accounts"
                : "Selected account";
    }

    private String holdingId(InvestmentTransaction transaction) {
        if (transaction.getPlaidInvestmentTransactionId() != null && !transaction.getPlaidInvestmentTransactionId().isBlank()) {
            return transaction.getPlaidInvestmentTransactionId();
        }
        return transaction.getId() == null ? "" : "dividend-" + transaction.getId();
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator.abs(), 2, RoundingMode.HALF_UP);
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

    private List<BigDecimal> sparkline(BigDecimal start, BigDecimal end) {
        List<BigDecimal> values = new ArrayList<>();
        BigDecimal delta = end.subtract(start).divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
        for (int i = 0; i < 6; i++) {
            values.add(start.add(delta.multiply(BigDecimal.valueOf(i))).setScale(2, RoundingMode.HALF_UP));
        }
        return values;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String logoText(String value) {
        String cleaned = fallback(value, "div").replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isBlank()) {
            return "div";
        }
        return cleaned.length() <= 3
                ? cleaned.toLowerCase(Locale.US)
                : cleaned.substring(0, 3).toLowerCase(Locale.US);
    }

    private BigDecimal marketValue(HoldingContext context) {
        return marketQuoteService.marketValue(context.holding(), context.security()).setScale(2, RoundingMode.HALF_UP);
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }

    private record HoldingContext(PlaidItem plaidItem, InvestmentHolding holding, InvestmentSecurity security) {
    }

    private record YieldResult(BigDecimal percent, BigDecimal count) {
    }
}
