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
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.accounts.Account;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.AccountsConnected;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.AllocationItem;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.ConnectedAccount;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.DayChangeMetric;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.DividendSnapshot;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.HoldingRow;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.Metrics;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.Money;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.MoneyMetric;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.Performance;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.PerformancePoint;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.TopPayer;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse.UpcomingDividends;
import com.Accounting.app.investments.InvestmentHolding;
import com.Accounting.app.investments.InvestmentHoldingRepo;
import com.Accounting.app.investments.InvestmentPortfolioSnapshot;
import com.Accounting.app.investments.InvestmentPortfolioSnapshotRepo;
import com.Accounting.app.investments.MarketQuoteService;
import com.Accounting.app.investments.InvestmentSecurity;
import com.Accounting.app.investments.InvestmentSecurityRepo;
import com.Accounting.app.investments.InvestmentTransaction;
import com.Accounting.app.investments.InvestmentTransactionRepo;
import com.Accounting.app.investments.InvestmentTransactionType;
import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.plaid.PlaidItemRepo;

@Service
public class PortfolioOverviewPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final DateTimeFormatter FULL_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter POINT_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final String[] COLORS = { "#2563eb", "#10b981", "#f97316", "#a855f7", "#64748b", "#eab308" };

    private final PlaidItemRepo plaidItemRepo;
    private final InvestmentHoldingRepo investmentHoldingRepo;
    private final InvestmentTransactionRepo investmentTransactionRepo;
    private final InvestmentPortfolioSnapshotRepo investmentPortfolioSnapshotRepo;
    private final InvestmentSecurityRepo investmentSecurityRepo;
    private final InvestmentStatusPolicy investmentStatusPolicy;
    private final MarketQuoteService marketQuoteService;

    public PortfolioOverviewPageServices(
            PlaidItemRepo plaidItemRepo,
            InvestmentHoldingRepo investmentHoldingRepo,
            InvestmentTransactionRepo investmentTransactionRepo,
            InvestmentPortfolioSnapshotRepo investmentPortfolioSnapshotRepo,
            InvestmentSecurityRepo investmentSecurityRepo,
            InvestmentStatusPolicy investmentStatusPolicy,
            MarketQuoteService marketQuoteService) {
        this.plaidItemRepo = plaidItemRepo;
        this.investmentHoldingRepo = investmentHoldingRepo;
        this.investmentTransactionRepo = investmentTransactionRepo;
        this.investmentPortfolioSnapshotRepo = investmentPortfolioSnapshotRepo;
        this.investmentSecurityRepo = investmentSecurityRepo;
        this.investmentStatusPolicy = investmentStatusPolicy;
        this.marketQuoteService = marketQuoteService;
    }

    @Transactional(readOnly = true)
    public PortfolioOverviewPageResponse portfolioOverviewPageResponse(
            String email,
            LocalDate from,
            LocalDate to,
            String accountScope) {
        List<PlaidItem> plaidItems = plaidItemRepo.findAllByUser_Email(email);
        List<HoldingContext> holdingContexts = scopedHoldings(plaidItems, accountScope);
        marketQuoteService.refreshQuotes(holdingContexts.stream().map(HoldingContext::security).filter(Objects::nonNull).toList());
        List<InvestmentTransaction> scopedTransactions = scopedTransactions(plaidItems, accountScope);
        DateRange range = resolveRange(holdingContexts, scopedTransactions, from, to);
        DateRange previousRange = previousRange(range);

        List<InvestmentTransaction> rangeTransactions = transactionsInRange(scopedTransactions, range);
        List<InvestmentPortfolioSnapshot> portfolioSnapshots = portfolioSnapshots(email, accountScope, range);
        String currency = resolveCurrency(holdingContexts);

        List<HoldingRow> holdings = holdings(holdingContexts, currency);
        BigDecimal totalPortfolioValue = holdings.stream()
                .map(HoldingRow::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCostBasis = holdings.stream()
                .map(HoldingRow::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(holdings.stream().map(HoldingRow::totalGainLossAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal totalGainLossAmount = totalPortfolioValue.subtract(totalCostBasis);
        BigDecimal totalGainLossPercent = percentChange(totalPortfolioValue, totalCostBasis);

        BigDecimal previousPortfolioValue = previousPortfolioValue(email, accountScope, range)
                .orElseGet(() -> estimatePreviousPortfolioValue(totalPortfolioValue, rangeTransactions));
        BigDecimal dayChangeAmount = holdings.stream()
                .map(HoldingRow::dayChangeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dayChangePercent = percent(dayChangeAmount, totalPortfolioValue.subtract(dayChangeAmount));

        BigDecimal dividendYtd = dividendAmount(scopedTransactions, YearMonth.from(range.to()).atDay(1).withDayOfYear(1), range.to());
        BigDecimal previousDividendYtd = dividendAmount(scopedTransactions, range.to().minusYears(1).withDayOfYear(1), range.to().minusYears(1));

        List<ConnectedAccount> connectedAccounts = connectedAccounts(holdingContexts, currency);
        List<AllocationItem> assetAllocation = assetAllocation(holdingContexts, totalPortfolioValue);

        return new PortfolioOverviewPageResponse(
                periodLabel(range),
                accountLabel(accountScope),
                new Metrics(
                        new MoneyMetric(
                                totalPortfolioValue,
                                currency,
                                percentChange(totalPortfolioValue, previousPortfolioValue),
                                "vs " + periodLabel(previousRange),
                                sparkline(previousPortfolioValue, totalPortfolioValue)),
                        new MoneyMetric(
                                totalGainLossAmount,
                                currency,
                                totalGainLossPercent,
                                "All time",
                                gainLossSparkline(holdings)),
                        new DayChangeMetric(
                                dayChangeAmount,
                                currency,
                                dayChangePercent,
                                dayChangeSparkline(holdings)),
                        new MoneyMetric(
                                dividendYtd,
                                currency,
                                percentChange(dividendYtd, previousDividendYtd),
                                "YTD",
                                dividendSparkline(scopedTransactions, range.to())),
                        new AccountsConnected(connectedAccounts.size(), "All accounts synced")),
                new Performance(
                        new Money(totalPortfolioValue, currency),
                        totalGainLossPercent,
                        totalGainLossAmount,
                        currency,
                        performanceSeries(range, totalPortfolioValue, portfolioSnapshots, rangeTransactions),
                        List.of("1D", "1W", "1M", "3M", "YTD", "1Y", "All"),
                        selectedRange(range)),
                assetAllocation,
                holdings,
                dividendSnapshot(scopedTransactions, range.to(), currency),
                connectedAccounts);
    }

    private List<HoldingContext> scopedHoldings(List<PlaidItem> plaidItems, String accountScope) {
        return plaidItems.stream()
                .flatMap(plaidItem -> investmentHoldingRepo.findAllByPlaidItem(plaidItem).stream()
                        .filter(holding -> matchesAccountScope(holding.getAccountId(), accountScope))
                        .map(holding -> new HoldingContext(plaidItem, holding, security(holding.getSecurityId()))))
                .toList();
    }

    private List<InvestmentTransaction> scopedTransactions(List<PlaidItem> plaidItems, String accountScope) {
        return plaidItems.stream()
                .flatMap(plaidItem -> investmentTransactionRepo.findAllByPlaidItem(plaidItem).stream())
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

        LocalDate earliestDate = transactions.stream()
                .map(InvestmentTransaction::getDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElseGet(() -> holdingContexts.stream()
                        .map(context -> context.holding().getSyncedAt())
                        .filter(Objects::nonNull)
                        .map(LocalDateTime::toLocalDate)
                        .min(Comparator.naturalOrder())
                        .orElse(latestDate));

        return earliestDate.isAfter(latestDate)
                ? new DateRange(latestDate, earliestDate)
                : new DateRange(earliestDate, latestDate);
    }

    private DateRange previousRange(DateRange range) {
        long days = range.to().toEpochDay() - range.from().toEpochDay();
        LocalDate previousTo = range.from().minusDays(1);
        return new DateRange(previousTo.minusDays(days), previousTo);
    }

    private List<InvestmentPortfolioSnapshot> portfolioSnapshots(String email, String accountScope, DateRange range) {
        return investmentPortfolioSnapshotRepo.findAllByEmailAndAccountIdAndSnapshotAtBetweenOrderBySnapshotAtAsc(
                email,
                accountScopeKey(accountScope),
                range.from().atStartOfDay(),
                range.to().plusDays(1).atStartOfDay());
    }

    private Optional<BigDecimal> previousPortfolioValue(String email, String accountScope, DateRange range) {
        return investmentPortfolioSnapshotRepo
                .findTopByEmailAndAccountIdAndSnapshotAtBeforeOrderBySnapshotAtDesc(
                        email,
                        accountScopeKey(accountScope),
                        range.from().atStartOfDay())
                .map(InvestmentPortfolioSnapshot::getTotalValue);
    }

    private String accountScopeKey(String accountScope) {
        return accountScope == null || accountScope.isBlank() || "all".equalsIgnoreCase(accountScope)
                ? "all"
                : accountScope;
    }
    private List<InvestmentTransaction> transactionsInRange(List<InvestmentTransaction> transactions, DateRange range) {
        return transactions.stream()
                .filter(transaction -> transaction.getDate() != null)
                .filter(transaction -> !transaction.getDate().isBefore(range.from()) && !transaction.getDate().isAfter(range.to()))
                .toList();
    }

    private List<HoldingRow> holdings(List<HoldingContext> holdingContexts, String currency) {
        return holdingContexts.stream()
                .map(context -> holdingRow(context, currency))
                .sorted(Comparator.comparing(HoldingRow::marketValue).reversed())
                .toList();
    }

    private HoldingRow holdingRow(HoldingContext context, String fallbackCurrency) {
        InvestmentHolding holding = context.holding();
        InvestmentSecurity security = context.security();
        BigDecimal marketValue = marketValue(context);
        BigDecimal quantity = holding.getQuantity();
        BigDecimal price = marketQuoteService.currentPrice(holding, security);

        GainLoss gainLoss = gainLoss(holding);
        BigDecimal previousPrice = marketQuoteService.previousClosePrice(holding, security);
        BigDecimal dayChangeAmount = quantity == null ? BigDecimal.ZERO : price.subtract(previousPrice).multiply(quantity);
        BigDecimal previousValue = quantity == null ? marketValue.subtract(dayChangeAmount) : previousPrice.multiply(quantity);
        BigDecimal dayChangePercent = percent(dayChangeAmount, previousValue);

        return new HoldingRow(
                holdingId(holding),
                fallback(holding.getTicker(), logoText(holding.getSecurityName()).toUpperCase(Locale.US)),
                fallback(holding.getSecurityName(), "Holding"),
                quantity,
                price.compareTo(BigDecimal.ZERO) == 0 ? null : price,
                fallback(holding.getCurrency(), fallbackCurrency),
                marketValue,
                dayChangeAmount.setScale(2, RoundingMode.HALF_UP),
                dayChangePercent,
                gainLoss.amount(),
                gainLoss.percent(),
                null,
                logoText(fallback(holding.getTicker(), holding.getSecurityName())));
    }

    private GainLoss gainLoss(InvestmentHolding holding) {
        BigDecimal buyCost = transactionAmountForHolding(holding, InvestmentTransactionType.BUY)
                .add(transactionAmountForHolding(holding, InvestmentTransactionType.REINVESTMENT))
                .add(transactionAmountForHolding(holding, InvestmentTransactionType.FEE));
        BigDecimal sellOffset = transactionAmountForHolding(holding, InvestmentTransactionType.SELL);
        BigDecimal costBasis = buyCost.subtract(sellOffset);
        BigDecimal marketValue = safe(holding.getInstitutionValue());
        BigDecimal amount = marketValue.subtract(costBasis);
        return new GainLoss(
                amount.setScale(2, RoundingMode.HALF_UP),
                percent(amount, costBasis));
    }

    private BigDecimal transactionAmountForHolding(InvestmentHolding holding, InvestmentTransactionType type) {
        return investmentTransactionRepo.findAllByPlaidItem(holding.getPlaidItem()).stream()
                .filter(transaction -> Objects.equals(transaction.getAccountId(), holding.getAccountId()))
                .filter(transaction -> Objects.equals(transaction.getSecurityId(), holding.getSecurityId()))
                .filter(transaction -> transaction.getType() == type)
                .map(InvestmentTransaction::getAmount)
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<AllocationItem> assetAllocation(List<HoldingContext> holdings, BigDecimal totalPortfolioValue) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (HoldingContext context : holdings) {
            String label = allocationLabel(context);
            totals.merge(label, marketValue(context), BigDecimal::add);
        }

        List<Map.Entry<String, BigDecimal>> entries = totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .toList();
        List<AllocationItem> allocation = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, BigDecimal> entry = entries.get(i);
            allocation.add(new AllocationItem(
                    entry.getKey(),
                    entry.getValue(),
                    percent(entry.getValue(), totalPortfolioValue),
                    COLORS[i % COLORS.length]));
        }
        return allocation;
    }

    private String allocationLabel(HoldingContext context) {
        InvestmentSecurity security = context.security();
        String value = String.join(" ",
                fallback(context.holding().getSecurityName(), ""),
                security == null ? "" : fallback(security.getType(), ""),
                security == null ? "" : fallback(security.getSubtype(), ""))
                .toLowerCase(Locale.US);
        if (value.contains("etf")) {
            return "ETFs";
        }
        if (value.contains("mutual")) {
            return "Funds";
        }
        if (value.contains("cash")) {
            return "Cash";
        }
        return "Stocks";
    }

    private List<ConnectedAccount> connectedAccounts(List<HoldingContext> holdingContexts, String currency) {
        Map<String, List<HoldingContext>> grouped = new LinkedHashMap<>();
        for (HoldingContext context : holdingContexts) {
            grouped.computeIfAbsent(accountGroupKey(context), ignored -> new ArrayList<>()).add(context);
        }

        return grouped.values().stream()
                .map(group -> connectedAccount(group, currency))
                .sorted(Comparator.comparing(ConnectedAccount::amount).reversed())
                .toList();
    }

    private ConnectedAccount connectedAccount(List<HoldingContext> group, String fallbackCurrency) {
        HoldingContext first = group.get(0);
        PlaidItem plaidItem = first.plaidItem();
        BigDecimal amount = group.stream()
                .map(this::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Account matched = matchingAccount(plaidItem, first.holding().getAccountId());

        return new ConnectedAccount(
                first.holding().getAccountId(),
                fallback(plaidItem.getInstitutionName(), "Linked institution"),
                fallback(first.holding().getAccountName(), "Investment account"),
                matched == null ? inferAccountType(first.holding().getAccountName()) : fallback(matched.getSubtype(), fallback(matched.getType(), "Investment")),
                amount,
                fallback(first.holding().getCurrency(), fallbackCurrency),
                investmentStatusPolicy.determineConnectedAccountStatus(
                        plaidItem,
                        group.stream().map(HoldingContext::holding).toList()),
                plaidItem.getInstitutionLogo(),
                institutionLogoText(plaidItem));
    }

    private Account matchingAccount(PlaidItem plaidItem, String accountId) {
        if (plaidItem.getAccounts() == null) {
            return null;
        }
        return plaidItem.getAccounts().stream()
                .filter(account -> Objects.equals(account.getPlaidAccountId(), accountId)
                        || Objects.equals(account.getAccountId(), accountId))
                .findFirst()
                .orElse(null);
    }

    private DividendSnapshot dividendSnapshot(List<InvestmentTransaction> transactions, LocalDate endDate, String currency) {
        BigDecimal ytd = dividendAmount(transactions, endDate.withDayOfYear(1), endDate);
        BigDecimal upcoming = BigDecimal.ZERO;

        Map<String, BigDecimal> payerTotals = new LinkedHashMap<>();
        for (InvestmentTransaction transaction : transactions) {
            if (transaction.getType() != InvestmentTransactionType.DIVIDEND) {
                continue;
            }
            payerTotals.merge(
                    fallback(transaction.getSecurityName(), "Dividend"),
                    safe(transaction.getAmount()).abs(),
                    BigDecimal::add);
        }

        Map.Entry<String, BigDecimal> top = payerTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        return new DividendSnapshot(
                new Money(ytd, currency),
                new UpcomingDividends(upcoming, currency, "Next 30 days"),
                new TopPayer(
                        top == null ? "No dividends" : top.getKey(),
                        top == null ? null : tickerForSecurityName(transactions, top.getKey()),
                        top == null ? BigDecimal.ZERO : top.getValue(),
                        currency));
    }

    private String tickerForSecurityName(List<InvestmentTransaction> transactions, String securityName) {
        return transactions.stream()
                .filter(transaction -> Objects.equals(transaction.getSecurityName(), securityName))
                .map(InvestmentTransaction::getTicker)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private BigDecimal dividendAmount(List<InvestmentTransaction> transactions, LocalDate from, LocalDate to) {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == InvestmentTransactionType.DIVIDEND)
                .filter(transaction -> transaction.getDate() != null)
                .filter(transaction -> !transaction.getDate().isBefore(from) && !transaction.getDate().isAfter(to))
                .map(InvestmentTransaction::getAmount)
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<PerformancePoint> performanceSeries(
            DateRange range,
            BigDecimal currentValue,
            List<InvestmentPortfolioSnapshot> snapshots,
            List<InvestmentTransaction> transactions) {
        if (snapshots != null && !snapshots.isEmpty()) {
            List<PerformancePoint> points = snapshots.stream()
                    .sorted(Comparator.comparing(InvestmentPortfolioSnapshot::getSnapshotAt))
                    .map(snapshot -> new PerformancePoint(
                            snapshot.getSnapshotAt().toLocalDate().format(POINT_LABEL_DATE),
                            safe(snapshot.getTotalValue()).setScale(2, RoundingMode.HALF_UP),
                            null))
                    .toList();

            PerformancePoint last = points.get(points.size() - 1);
            if (!last.date().equals(range.to().format(POINT_LABEL_DATE))) {
                List<PerformancePoint> withCurrent = new ArrayList<>(points);
                withCurrent.add(new PerformancePoint(
                        range.to().format(POINT_LABEL_DATE),
                        currentValue.setScale(2, RoundingMode.HALF_UP),
                        null));
                return withCurrent;
            }
            return points;
        }

        List<PerformancePoint> series = new ArrayList<>();
        long totalDays = Math.max(1, range.to().toEpochDay() - range.from().toEpochDay());
        BigDecimal netFlow = transactions.stream()
                .map(this::cashEffect)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal startValue = currentValue.subtract(netFlow);
        BigDecimal dailyStep = currentValue.subtract(startValue)
                .divide(BigDecimal.valueOf(totalDays), 4, RoundingMode.HALF_UP);

        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(stepDays(range))) {
            long offset = day.toEpochDay() - range.from().toEpochDay();
            BigDecimal value = startValue.add(dailyStep.multiply(BigDecimal.valueOf(offset)))
                    .setScale(2, RoundingMode.HALF_UP);
            series.add(new PerformancePoint(day.format(POINT_LABEL_DATE), value, null));
        }

        if (series.isEmpty() || !series.get(series.size() - 1).date().equals(range.to().format(POINT_LABEL_DATE))) {
            series.add(new PerformancePoint(range.to().format(POINT_LABEL_DATE), currentValue.setScale(2, RoundingMode.HALF_UP), null));
        }

        return series;
    }

    private long stepDays(DateRange range) {
        long days = Math.max(1, range.to().toEpochDay() - range.from().toEpochDay());
        if (days <= 7) {
            return 1;
        }
        if (days <= 31) {
            return 3;
        }
        return Math.max(7, days / 10);
    }

    private BigDecimal cashEffect(InvestmentTransaction transaction) {
        BigDecimal amount = safe(transaction.getAmount()).abs();
        return switch (transaction.getType()) {
            case BUY, REINVESTMENT, FEE -> amount;
            case SELL, DIVIDEND -> amount.negate();
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal estimatePreviousPortfolioValue(BigDecimal currentValue, List<InvestmentTransaction> rangeTransactions) {
        BigDecimal netFlow = rangeTransactions.stream()
                .map(this::cashEffect)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previous = currentValue.subtract(netFlow);
        return previous.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : previous;
    }

    private List<BigDecimal> sparkline(BigDecimal start, BigDecimal end) {
        List<BigDecimal> values = new ArrayList<>();
        BigDecimal delta = end.subtract(start).divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
        for (int i = 0; i < 6; i++) {
            values.add(start.add(delta.multiply(BigDecimal.valueOf(i))).setScale(2, RoundingMode.HALF_UP));
        }
        return values;
    }

    private List<BigDecimal> gainLossSparkline(List<HoldingRow> holdings) {
        BigDecimal total = holdings.stream().map(HoldingRow::totalGainLossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sparkline(total.multiply(BigDecimal.valueOf(0.7)).setScale(2, RoundingMode.HALF_UP), total);
    }

    private List<BigDecimal> dayChangeSparkline(List<HoldingRow> holdings) {
        BigDecimal total = holdings.stream().map(HoldingRow::dayChangeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return List.of(
                total.multiply(BigDecimal.valueOf(0.4)).setScale(2, RoundingMode.HALF_UP),
                total.multiply(BigDecimal.valueOf(0.6)).setScale(2, RoundingMode.HALF_UP),
                total.multiply(BigDecimal.valueOf(0.3)).setScale(2, RoundingMode.HALF_UP),
                total.multiply(BigDecimal.valueOf(0.8)).setScale(2, RoundingMode.HALF_UP),
                total.multiply(BigDecimal.valueOf(0.5)).setScale(2, RoundingMode.HALF_UP),
                total.setScale(2, RoundingMode.HALF_UP));
    }

    private List<BigDecimal> dividendSparkline(List<InvestmentTransaction> transactions, LocalDate endDate) {
        List<BigDecimal> points = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = YearMonth.from(endDate).minusMonths(i);
            points.add(dividendAmount(transactions, month.atDay(1), month.atEndOfMonth()));
        }
        return points;
    }

    private InvestmentSecurity security(String securityId) {
        if (securityId == null || securityId.isBlank()) {
            return null;
        }
        return investmentSecurityRepo.findBySecurityId(securityId).orElse(null);
    }

    private boolean matchesAccountScope(String accountId, String accountScope) {
        return accountScope == null
                || accountScope.isBlank()
                || "all".equalsIgnoreCase(accountScope)
                || Objects.equals(accountId, accountScope);
    }

    private String resolveCurrency(List<HoldingContext> holdings) {
        return holdings.stream()
                .map(context -> context.holding().getCurrency())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(DEFAULT_CURRENCY);
    }

    private String periodLabel(DateRange range) {
        return range.from().format(SHORT_LABEL_DATE) + " - " + range.to().format(FULL_LABEL_DATE);
    }

    private String accountLabel(String accountScope) {
        return accountScope == null || accountScope.isBlank() || "all".equalsIgnoreCase(accountScope)
                ? "All accounts"
                : "Selected account";
    }

    private String selectedRange(DateRange range) {
        long days = Math.max(1, range.to().toEpochDay() - range.from().toEpochDay()) + 1;
        if (days <= 1) {
            return "1D";
        }
        if (days <= 7) {
            return "1W";
        }
        if (days <= 31) {
            return "1M";
        }
        if (days <= 92) {
            return "3M";
        }
        if (range.from().equals(YearMonth.from(range.to()).atDay(1).withDayOfYear(1))) {
            return "YTD";
        }
        if (days <= 366) {
            return "1Y";
        }
        return "All";
    }

    private String holdingId(InvestmentHolding holding) {
        return (fallback(holding.getAccountId(), "account") + "-" + fallback(holding.getSecurityId(), fallback(holding.getTicker(), "holding")))
                .toLowerCase(Locale.US);
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator.abs(), 1, RoundingMode.HALF_UP);
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

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String logoText(String value) {
        String cleaned = fallback(value, "pf").replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isBlank()) {
            return "pf";
        }
        return cleaned.length() <= 3
                ? cleaned.toLowerCase(Locale.US)
                : cleaned.substring(0, 3).toLowerCase(Locale.US);
    }

    private String institutionLogoText(PlaidItem plaidItem) {
        String name = fallback(plaidItem.getInstitutionName(), "Linked institution");
        String[] words = name.trim().split("\\s+");
        if (words.length >= 2) {
            return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(Locale.US);
        }
        return logoText(name).toUpperCase(Locale.US);
    }

    private String inferAccountType(String accountName) {
        String value = fallback(accountName, "").toUpperCase(Locale.US);
        if (value.contains("TFSA")) {
            return "TFSA";
        }
        if (value.contains("RRSP")) {
            return "RRSP";
        }
        if (value.contains("IRA")) {
            return "IRA";
        }
        if (value.contains("BROKER")) {
            return "Brokerage";
        }
        return "Investment";
    }

    private String accountGroupKey(HoldingContext context) {
        return fallback(context.plaidItem().getPlaidItemId(), String.valueOf(context.plaidItem().getItemId()))
                + ":"
                + fallback(context.holding().getAccountId(), context.holding().getAccountName());
    }

    private BigDecimal marketValue(HoldingContext context) {
        return marketQuoteService.marketValue(context.holding(), context.security()).setScale(2, RoundingMode.HALF_UP);
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }

    private record HoldingContext(PlaidItem plaidItem, InvestmentHolding holding, InvestmentSecurity security) {
    }

    private record GainLoss(BigDecimal amount, BigDecimal percent) {
    }
}


