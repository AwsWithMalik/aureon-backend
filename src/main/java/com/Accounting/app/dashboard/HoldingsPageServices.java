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

import com.Accounting.app.dashboard.dto.HoldingsPageResponse;
import com.Accounting.app.dashboard.dto.HoldingsPageResponse.AccountExposure;
import com.Accounting.app.dashboard.dto.HoldingsPageResponse.CashPosition;
import com.Accounting.app.dashboard.dto.HoldingsPageResponse.GainerLoser;
import com.Accounting.app.dashboard.dto.HoldingsPageResponse.HoldingRow;
import com.Accounting.app.dashboard.dto.HoldingsPageResponse.Insight;
import com.Accounting.app.dashboard.dto.HoldingsPageResponse.Metrics;
import com.Accounting.app.dashboard.dto.HoldingsPageResponse.MoneyMetric;
import com.Accounting.app.dashboard.dto.HoldingsPageResponse.OpenPositions;
import com.Accounting.app.dashboard.dto.HoldingsPageResponse.SectorPerformance;
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
public class HoldingsPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final DateTimeFormatter FULL_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final String[] ACCOUNT_COLORS = { "#2563eb", "#10b981", "#f97316", "#a855f7", "#64748b", "#eab308" };

    private final PlaidItemRepo plaidItemRepo;
    private final InvestmentHoldingRepo investmentHoldingRepo;
    private final InvestmentTransactionRepo investmentTransactionRepo;
    private final InvestmentSecurityRepo investmentSecurityRepo;
    private final MarketQuoteService marketQuoteService;

    public HoldingsPageServices(
            PlaidItemRepo plaidItemRepo,
            InvestmentHoldingRepo investmentHoldingRepo,
            InvestmentTransactionRepo investmentTransactionRepo,
            InvestmentSecurityRepo investmentSecurityRepo,
            MarketQuoteService marketQuoteService) {
        this.plaidItemRepo = plaidItemRepo;
        this.investmentHoldingRepo = investmentHoldingRepo;
        this.investmentTransactionRepo = investmentTransactionRepo;
        this.investmentSecurityRepo = investmentSecurityRepo;
        this.marketQuoteService = marketQuoteService;
    }

    @Transactional(readOnly = true)
    public HoldingsPageResponse holdingsPageResponse(String email, LocalDate from, LocalDate to, String accountScope) {
        List<PlaidItem> plaidItems = plaidItemRepo.findAllByUser_Email(email);
        List<HoldingContext> holdingContexts = scopedHoldings(plaidItems, accountScope);
        marketQuoteService.refreshQuotes(holdingContexts.stream().map(HoldingContext::security).filter(Objects::nonNull).toList());
        List<InvestmentTransaction> scopedTransactions = scopedTransactions(plaidItems, accountScope);
        DateRange range = resolveRange(holdingContexts, scopedTransactions, from, to);
        DateRange previousRange = previousRange(range);
        List<InvestmentTransaction> rangeTransactions = transactionsInRange(scopedTransactions, range);

        String currency = resolveCurrency(holdingContexts);
        List<HoldingRow> holdings = holdings(holdingContexts, currency);
        BigDecimal totalHoldingsValue = holdings.stream()
                .map(HoldingRow::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousValue = estimatePreviousValue(totalHoldingsValue, rangeTransactions);
        GainerLoser topGainer = topGainer(holdings, currency);
        GainerLoser topLoser = topLoser(holdings, currency);
        BigDecimal cashAmount = cashAmount(holdingContexts);

        return new HoldingsPageResponse(
                periodLabel(range),
                accountLabel(accountScope),
                new Metrics(
                        new MoneyMetric(
                                totalHoldingsValue,
                                currency,
                                percentChange(totalHoldingsValue, previousValue),
                                "vs " + periodLabel(previousRange),
                                sparkline(previousValue, totalHoldingsValue)),
                        new OpenPositions(
                                holdings.size(),
                                "Open positions",
                                positionSparkline(holdings.size())),
                        topGainer,
                        topLoser,
                        new CashPosition(
                                cashAmount,
                                currency,
                                percent(cashAmount, totalHoldingsValue),
                                cashSparkline(cashAmount))),
                holdings,
                sectorPerformance(holdingContexts),
                insights(holdings, cashAmount, totalHoldingsValue),
                accountExposure(holdingContexts, currency, totalHoldingsValue));
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
                .toList();
    }

    private List<HoldingRow> holdings(List<HoldingContext> holdingContexts, String fallbackCurrency) {
        BigDecimal totalValue = holdingContexts.stream()
                .map(context -> safe(context.holding().getInstitutionValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, String> accountColors = accountColors(holdingContexts);

        return holdingContexts.stream()
                .map(context -> holdingRow(context, fallbackCurrency, totalValue, accountColors))
                .sorted(Comparator.comparing(HoldingRow::marketValue).reversed())
                .toList();
    }

    private HoldingRow holdingRow(
            HoldingContext context,
            String fallbackCurrency,
            BigDecimal totalValue,
            Map<String, String> accountColors) {
        InvestmentHolding holding = context.holding();
        InvestmentSecurity security = context.security();
        BigDecimal marketValue = marketValue(context);
        BigDecimal quantity = holding.getQuantity();
        BigDecimal price = marketQuoteService.currentPrice(holding, security);

        GainLoss gainLoss = gainLoss(holding);
        BigDecimal averageCost = averageCost(holding);
        BigDecimal previousPrice = marketQuoteService.previousClosePrice(holding, security);
        BigDecimal dayChangeAmount = quantity == null ? BigDecimal.ZERO : price.subtract(previousPrice).multiply(quantity);
        BigDecimal previousValue = quantity == null ? marketValue.subtract(dayChangeAmount) : previousPrice.multiply(quantity);
        BigDecimal dayChangePercent = percent(dayChangeAmount, previousValue);

        return new HoldingRow(
                holdingId(holding),
                fallback(holding.getTicker(), logoText(holding.getSecurityName()).toUpperCase(Locale.US)),
                fallback(holding.getSecurityName(), "Holding"),
                fallback(holding.getAccountName(), "Investment account"),
                accountColors.getOrDefault(accountGroupKey(context), ACCOUNT_COLORS[0]),
                logoText(fallback(holding.getTicker(), holding.getSecurityName())),
                null,
                quantity,
                averageCost.compareTo(BigDecimal.ZERO) == 0 ? null : averageCost,
                price.compareTo(BigDecimal.ZERO) == 0 ? null : price,
                fallback(holding.getCurrency(), fallbackCurrency),
                marketValue,
                percent(marketValue, totalValue),
                dayChangeAmount.setScale(2, RoundingMode.HALF_UP),
                dayChangePercent,
                gainLoss.amount(),
                gainLoss.percent());
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

    private BigDecimal averageCost(InvestmentHolding holding) {
        BigDecimal buyCost = transactionAmountForHolding(holding, InvestmentTransactionType.BUY)
                .add(transactionAmountForHolding(holding, InvestmentTransactionType.REINVESTMENT));
        BigDecimal quantity = holding.getQuantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return buyCost.divide(quantity, 4, RoundingMode.HALF_UP);
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

    private GainerLoser topGainer(List<HoldingRow> holdings, String currency) {
        HoldingRow row = holdings.stream()
                .max(Comparator.comparing(HoldingRow::totalGainLossPercent))
                .orElse(null);
        if (row == null) {
            return new GainerLoser("No holdings", BigDecimal.ZERO, BigDecimal.ZERO, currency, List.of(BigDecimal.ZERO));
        }
        return new GainerLoser(
                row.symbol(),
                row.totalGainLossAmount(),
                row.totalGainLossPercent(),
                row.currency(),
                sparkline(row.totalGainLossAmount().multiply(BigDecimal.valueOf(0.6)), row.totalGainLossAmount()));
    }

    private GainerLoser topLoser(List<HoldingRow> holdings, String currency) {
        HoldingRow row = holdings.stream()
                .min(Comparator.comparing(HoldingRow::totalGainLossPercent))
                .orElse(null);
        if (row == null) {
            return new GainerLoser("No holdings", BigDecimal.ZERO, BigDecimal.ZERO, currency, List.of(BigDecimal.ZERO));
        }
        return new GainerLoser(
                row.symbol(),
                row.totalGainLossAmount(),
                row.totalGainLossPercent(),
                row.currency(),
                sparkline(row.totalGainLossAmount().multiply(BigDecimal.valueOf(1.4)), row.totalGainLossAmount()));
    }

    private BigDecimal cashAmount(List<HoldingContext> holdingContexts) {
        return holdingContexts.stream()
                .filter(this::isCashLike)
                .map(this::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isCashLike(HoldingContext context) {
        String value = String.join(" ",
                fallback(context.holding().getSecurityName(), ""),
                context.security() == null ? "" : fallback(context.security().getType(), ""),
                context.security() == null ? "" : fallback(context.security().getSubtype(), ""))
                .toLowerCase(Locale.US);
        return value.contains("cash");
    }

    private List<SectorPerformance> sectorPerformance(List<HoldingContext> holdingContexts) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (HoldingContext context : holdingContexts) {
            String label = sectorLabel(context);
            totals.merge(label, marketValue(context), BigDecimal::add);
        }

        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> new SectorPerformance(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    private String sectorLabel(HoldingContext context) {
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
        if (value.contains("mutual")) {
            return "Funds";
        }
        if (value.contains("cash")) {
            return "Cash";
        }
        return "Equities";
    }

    private List<Insight> insights(List<HoldingRow> holdings, BigDecimal cashAmount, BigDecimal totalHoldingsValue) {
        List<Insight> insights = new ArrayList<>();
        if (!holdings.isEmpty()) {
            HoldingRow largest = holdings.get(0);
            insights.add(new Insight(
                    "largest-position",
                    "Largest position",
                    largest.name() + " is your biggest holding by market value.",
                    largest.allocationPercent().setScale(1, RoundingMode.HALF_UP).toPlainString() + "%"));
        }

        insights.add(new Insight(
                "cash-position",
                "Cash position",
                "Cash-like holdings as a share of the scoped portfolio.",
                percent(cashAmount, totalHoldingsValue).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%"));

        long profitable = holdings.stream()
                .filter(holding -> holding.totalGainLossAmount().compareTo(BigDecimal.ZERO) > 0)
                .count();
        insights.add(new Insight(
                "profitable-holdings",
                "Profitable holdings",
                profitable + " open positions are above cost basis.",
                profitable + "/" + holdings.size()));

        return insights;
    }

    private List<AccountExposure> accountExposure(List<HoldingContext> holdingContexts, String currency, BigDecimal totalValue) {
        Map<String, ExposureAccumulator> totals = new LinkedHashMap<>();
        Map<String, String> colors = accountColors(holdingContexts);

        for (HoldingContext context : holdingContexts) {
            String key = accountGroupKey(context);
            ExposureAccumulator accumulator = totals.computeIfAbsent(key, ignored -> new ExposureAccumulator(
                    fallback(context.holding().getAccountName(), "Investment account"),
                    BigDecimal.ZERO));
            accumulator.amount = accumulator.amount.add(marketValue(context));
        }

        List<AccountExposure> exposure = new ArrayList<>();
        int colorIndex = 0;
        for (Map.Entry<String, ExposureAccumulator> entry : totals.entrySet()) {
            ExposureAccumulator value = entry.getValue();
            exposure.add(new AccountExposure(
                    value.label,
                    value.amount.setScale(2, RoundingMode.HALF_UP),
                    currency,
                    percent(value.amount, totalValue),
                    colors.getOrDefault(entry.getKey(), ACCOUNT_COLORS[colorIndex++ % ACCOUNT_COLORS.length])));
        }

        exposure.sort(Comparator.comparing(AccountExposure::amount).reversed());
        return exposure;
    }

    private Map<String, String> accountColors(List<HoldingContext> holdingContexts) {
        Map<String, String> colors = new LinkedHashMap<>();
        int index = 0;
        for (HoldingContext context : holdingContexts) {
            String key = accountGroupKey(context);
            if (!colors.containsKey(key)) {
                colors.put(key, ACCOUNT_COLORS[index % ACCOUNT_COLORS.length]);
                index++;
            }
        }
        return colors;
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

    private BigDecimal estimatePreviousValue(BigDecimal currentValue, List<InvestmentTransaction> rangeTransactions) {
        BigDecimal netFlow = rangeTransactions.stream()
                .map(this::cashEffect)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previous = currentValue.subtract(netFlow);
        return previous.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : previous;
    }

    private BigDecimal cashEffect(InvestmentTransaction transaction) {
        BigDecimal amount = safe(transaction.getAmount()).abs();
        return switch (transaction.getType()) {
            case BUY, REINVESTMENT, FEE -> amount;
            case SELL, DIVIDEND -> amount.negate();
            default -> BigDecimal.ZERO;
        };
    }

    private List<BigDecimal> sparkline(BigDecimal start, BigDecimal end) {
        List<BigDecimal> values = new ArrayList<>();
        BigDecimal delta = end.subtract(start).divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
        for (int i = 0; i < 6; i++) {
            values.add(start.add(delta.multiply(BigDecimal.valueOf(i))).setScale(2, RoundingMode.HALF_UP));
        }
        return values;
    }

    private List<BigDecimal> positionSparkline(int openPositions) {
        BigDecimal value = BigDecimal.valueOf(openPositions);
        return List.of(value, value, value, value, value, value);
    }

    private List<BigDecimal> cashSparkline(BigDecimal cashAmount) {
        return List.of(
                cashAmount.multiply(BigDecimal.valueOf(0.9)).setScale(2, RoundingMode.HALF_UP),
                cashAmount.multiply(BigDecimal.valueOf(1.0)).setScale(2, RoundingMode.HALF_UP),
                cashAmount.multiply(BigDecimal.valueOf(0.95)).setScale(2, RoundingMode.HALF_UP),
                cashAmount.multiply(BigDecimal.valueOf(1.05)).setScale(2, RoundingMode.HALF_UP),
                cashAmount.multiply(BigDecimal.valueOf(0.98)).setScale(2, RoundingMode.HALF_UP),
                cashAmount.setScale(2, RoundingMode.HALF_UP));
    }

    private String periodLabel(DateRange range) {
        return range.from().format(SHORT_LABEL_DATE) + " - " + range.to().format(FULL_LABEL_DATE);
    }

    private String accountLabel(String accountScope) {
        return accountScope == null || accountScope.isBlank() || "all".equalsIgnoreCase(accountScope)
                ? "All accounts"
                : "Selected account";
    }

    private String holdingId(InvestmentHolding holding) {
        return (fallback(holding.getAccountId(), "account") + "-" + fallback(holding.getSecurityId(), fallback(holding.getTicker(), "holding")))
                .toLowerCase(Locale.US);
    }

    private String accountGroupKey(HoldingContext context) {
        return fallback(context.plaidItem().getPlaidItemId(), String.valueOf(context.plaidItem().getItemId()))
                + ":"
                + fallback(context.holding().getAccountId(), context.holding().getAccountName());
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
        String cleaned = fallback(value, "hd").replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isBlank()) {
            return "hd";
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

    private record GainLoss(BigDecimal amount, BigDecimal percent) {
    }

    private static final class ExposureAccumulator {
        private final String label;
        private BigDecimal amount;

        private ExposureAccumulator(String label, BigDecimal amount) {
            this.label = label;
            this.amount = amount;
        }
    }
}
