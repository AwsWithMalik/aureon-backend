package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.dashboard.dto.CapitalGainsPageResponse;
import com.Accounting.app.dashboard.dto.CapitalGainsPageResponse.ActivityRow;
import com.Accounting.app.dashboard.dto.CapitalGainsPageResponse.CumulativeGainPoint;
import com.Accounting.app.dashboard.dto.CapitalGainsPageResponse.Insight;
import com.Accounting.app.dashboard.dto.CapitalGainsPageResponse.Metrics;
import com.Accounting.app.dashboard.dto.CapitalGainsPageResponse.MoneyMetric;
import com.Accounting.app.dashboard.dto.CapitalGainsPageResponse.ReviewItem;
import com.Accounting.app.dashboard.dto.CapitalGainsPageResponse.TaxSnapshotItem;
import com.Accounting.app.investments.InvestmentHolding;
import com.Accounting.app.investments.InvestmentHoldingRepo;
import com.Accounting.app.investments.InvestmentSecurity;
import com.Accounting.app.investments.InvestmentSecurityRepo;
import com.Accounting.app.investments.InvestmentTransaction;
import com.Accounting.app.investments.InvestmentTransactionRepo;
import com.Accounting.app.investments.InvestmentTransactionType;
import com.Accounting.app.investments.MarketQuoteService;
import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.plaid.PlaidItemRepo;

@Service
public class CapitalGainsPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final DateTimeFormatter FULL_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter MONTH_LABEL_DATE = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);

    private final PlaidItemRepo plaidItemRepo;
    private final InvestmentHoldingRepo investmentHoldingRepo;
    private final InvestmentTransactionRepo investmentTransactionRepo;
    private final InvestmentSecurityRepo investmentSecurityRepo;
    private final InvestmentStatusPolicy investmentStatusPolicy;
    private final MarketQuoteService marketQuoteService;

    public CapitalGainsPageServices(
            PlaidItemRepo plaidItemRepo,
            InvestmentHoldingRepo investmentHoldingRepo,
            InvestmentTransactionRepo investmentTransactionRepo,
            InvestmentSecurityRepo investmentSecurityRepo,
            InvestmentStatusPolicy investmentStatusPolicy,
            MarketQuoteService marketQuoteService) {
        this.plaidItemRepo = plaidItemRepo;
        this.investmentHoldingRepo = investmentHoldingRepo;
        this.investmentTransactionRepo = investmentTransactionRepo;
        this.investmentSecurityRepo = investmentSecurityRepo;
        this.investmentStatusPolicy = investmentStatusPolicy;
        this.marketQuoteService = marketQuoteService;
    }

    @Transactional(readOnly = true)
    public CapitalGainsPageResponse capitalGainsPageResponse(
            String email,
            LocalDate from,
            LocalDate to,
            String accountScope) {
        List<PlaidItem> plaidItems = plaidItemRepo.findAllByUser_Email(email);
        List<HoldingContext> holdingContexts = scopedHoldings(plaidItems, accountScope).stream()
                .filter(context -> !isCashLike(context))
                .toList();
        marketQuoteService.refreshQuotes(holdingContexts.stream().map(HoldingContext::security).filter(Objects::nonNull).toList());

        List<InvestmentTransaction> scopedTransactions = scopedTransactions(plaidItems, accountScope);
        DateRange range = resolveRange(holdingContexts, scopedTransactions, from, to);
        DateRange previousRange = previousRange(range);
        List<InvestmentTransaction> rangeTransactions = transactionsInRange(scopedTransactions, range);
        List<InvestmentTransaction> previousTransactions = transactionsInRange(scopedTransactions, previousRange);

        String currency = resolveCurrency(holdingContexts, scopedTransactions);
        List<TransactionGain> rangeSellGains = sellGains(rangeTransactions, scopedTransactions, currency);
        List<TransactionGain> previousSellGains = sellGains(previousTransactions, scopedTransactions, currency);
        List<HoldingGain> holdingGains = holdingGains(holdingContexts, currency);

        BigDecimal realizedGains = positiveGains(rangeSellGains);
        BigDecimal previousRealizedGains = positiveGains(previousSellGains);
        BigDecimal capitalLosses = losses(rangeSellGains);
        BigDecimal previousCapitalLosses = losses(previousSellGains);
        BigDecimal unrealizedGains = positiveHoldingGains(holdingGains);
        BigDecimal netGain = realizedGains.add(unrealizedNet(holdingGains)).subtract(capitalLosses)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousNetGain = previousRealizedGains.add(unrealizedNet(holdingGains)).subtract(previousCapitalLosses)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableGainEstimate = taxableGainEstimate(rangeSellGains);
        BigDecimal previousTaxableGainEstimate = taxableGainEstimate(previousSellGains);

        return new CapitalGainsPageResponse(
                periodLabel(range),
                accountLabel(accountScope),
                range.to().getYear() + " YTD",
                new Metrics(
                        metric(realizedGains, previousRealizedGains, currency, previousRange, sellSparkline(scopedTransactions, range.to(), MetricMode.REALIZED_GAIN)),
                        metric(unrealizedGains, unrealizedGains, currency, previousRange, flatSparkline(unrealizedGains)),
                        metric(capitalLosses, previousCapitalLosses, currency, previousRange, sellSparkline(scopedTransactions, range.to(), MetricMode.LOSS)),
                        metric(netGain, previousNetGain, currency, previousRange, netSparkline(scopedTransactions, range.to(), unrealizedNet(holdingGains))),
                        metric(taxableGainEstimate, previousTaxableGainEstimate, currency, previousRange, flatSparkline(taxableGainEstimate))),
                cumulativeGains(scopedTransactions, holdingGains, range, currency),
                taxSnapshot(rangeSellGains, taxableGainEstimate, currency, range),
                insights(rangeSellGains, holdingGains),
                reviewItems(rangeSellGains),
                activityRows(rangeSellGains, currency));
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
                .sorted(Comparator.comparing(InvestmentTransaction::getDate).reversed()
                        .thenComparing(transaction -> fallback(transaction.getSecurityName(), "")))
                .toList();
    }

    private List<TransactionGain> sellGains(
            List<InvestmentTransaction> transactions,
            List<InvestmentTransaction> allTransactions,
            String currency) {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == InvestmentTransactionType.SELL)
                .map(transaction -> transactionGain(transaction, allTransactions, currency))
                .toList();
    }

    private TransactionGain transactionGain(
            InvestmentTransaction transaction,
            List<InvestmentTransaction> allTransactions,
            String currency) {
        BigDecimal proceeds = safe(transaction.getAmount()).abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal costBasis = estimatedSellCostBasis(transaction, allTransactions);
        BigDecimal gainLoss = costBasis == null ? null : proceeds.subtract(costBasis).setScale(2, RoundingMode.HALF_UP);
        return new TransactionGain(transaction, proceeds, costBasis, gainLoss, costBasis == null, fallback(transaction.getCurrency(), currency));
    }

    private BigDecimal estimatedSellCostBasis(InvestmentTransaction sell, List<InvestmentTransaction> allTransactions) {
        BigDecimal sellQuantity = sell.getQuantity() == null ? BigDecimal.ZERO : sell.getQuantity().abs();
        if (sellQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        List<InvestmentTransaction> buys = allTransactions.stream()
                .filter(transaction -> sameHolding(transaction, sell))
                .filter(transaction -> transaction.getType() == InvestmentTransactionType.BUY || transaction.getType() == InvestmentTransactionType.REINVESTMENT)
                .filter(transaction -> transaction.getDate() == null || sell.getDate() == null || !transaction.getDate().isAfter(sell.getDate()))
                .toList();

        BigDecimal buyQuantity = buys.stream()
                .map(InvestmentTransaction::getQuantity)
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal buyAmount = buys.stream()
                .map(InvestmentTransaction::getAmount)
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (buyQuantity.compareTo(BigDecimal.ZERO) == 0 || buyAmount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal averageCost = buyAmount.divide(buyQuantity, 8, RoundingMode.HALF_UP);
        return averageCost.multiply(sellQuantity).setScale(2, RoundingMode.HALF_UP);
    }

    private List<HoldingGain> holdingGains(List<HoldingContext> holdingContexts, String currency) {
        return holdingContexts.stream()
                .map(context -> holdingGain(context, currency))
                .toList();
    }

    private HoldingGain holdingGain(HoldingContext context, String currency) {
        InvestmentHolding holding = context.holding();
        BigDecimal marketValue = marketQuoteService.marketValue(holding, context.security()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal buyCost = transactionAmountForHolding(holding, InvestmentTransactionType.BUY)
                .add(transactionAmountForHolding(holding, InvestmentTransactionType.REINVESTMENT))
                .add(transactionAmountForHolding(holding, InvestmentTransactionType.FEE));
        BigDecimal sellOffset = transactionAmountForHolding(holding, InvestmentTransactionType.SELL);
        BigDecimal costBasis = buyCost.subtract(sellOffset);
        if (costBasis.compareTo(BigDecimal.ZERO) < 0) {
            costBasis = BigDecimal.ZERO;
        }
        BigDecimal gainLoss = marketValue.subtract(costBasis).setScale(2, RoundingMode.HALF_UP);
        return new HoldingGain(holding, marketValue, costBasis.setScale(2, RoundingMode.HALF_UP), gainLoss, fallback(holding.getCurrency(), currency));
    }

    private BigDecimal transactionAmountForHolding(InvestmentHolding holding, InvestmentTransactionType type) {
        return investmentTransactionRepo.findAllByPlaidItem(holding.getPlaidItem()).stream()
                .filter(transaction -> Objects.equals(transaction.getAccountId(), holding.getAccountId()))
                .filter(transaction -> sameSecurity(transaction, holding))
                .filter(transaction -> transaction.getType() == type)
                .map(InvestmentTransaction::getAmount)
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal positiveGains(List<TransactionGain> gains) {
        return gains.stream()
                .map(TransactionGain::gainLoss)
                .filter(Objects::nonNull)
                .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal losses(List<TransactionGain> gains) {
        return gains.stream()
                .map(TransactionGain::gainLoss)
                .filter(Objects::nonNull)
                .filter(value -> value.compareTo(BigDecimal.ZERO) < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal positiveHoldingGains(List<HoldingGain> gains) {
        return gains.stream()
                .map(HoldingGain::gainLoss)
                .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal unrealizedNet(List<HoldingGain> gains) {
        return gains.stream()
                .map(HoldingGain::gainLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal taxableGainEstimate(List<TransactionGain> gains) {
        BigDecimal taxableNet = gains.stream()
                .filter(gain -> !isRegisteredAccount(gain.transaction().getAccountName()))
                .map(TransactionGain::gainLoss)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (taxableNet.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return taxableNet.multiply(BigDecimal.valueOf(0.50)).setScale(2, RoundingMode.HALF_UP);
    }

    private MoneyMetric metric(
            BigDecimal current,
            BigDecimal previous,
            String currency,
            DateRange previousRange,
            List<BigDecimal> sparkline) {
        return new MoneyMetric(
                current.setScale(2, RoundingMode.HALF_UP),
                currency,
                percentChange(current, previous),
                "vs " + periodLabel(previousRange),
                sparkline);
    }

    private List<BigDecimal> sellSparkline(List<InvestmentTransaction> transactions, LocalDate endDate, MetricMode mode) {
        List<BigDecimal> values = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = YearMonth.from(endDate).minusMonths(i);
            List<InvestmentTransaction> monthTransactions = transactions.stream()
                    .filter(transaction -> transaction.getDate() != null)
                    .filter(transaction -> YearMonth.from(transaction.getDate()).equals(month))
                    .toList();
            List<TransactionGain> monthGains = sellGains(monthTransactions, transactions, DEFAULT_CURRENCY);
            values.add(mode == MetricMode.LOSS ? losses(monthGains) : positiveGains(monthGains));
        }
        return values;
    }

    private List<BigDecimal> netSparkline(List<InvestmentTransaction> transactions, LocalDate endDate, BigDecimal unrealizedNet) {
        List<BigDecimal> values = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = YearMonth.from(endDate).minusMonths(i);
            List<InvestmentTransaction> monthTransactions = transactions.stream()
                    .filter(transaction -> transaction.getDate() != null)
                    .filter(transaction -> YearMonth.from(transaction.getDate()).equals(month))
                    .toList();
            List<TransactionGain> monthGains = sellGains(monthTransactions, transactions, DEFAULT_CURRENCY);
            values.add(positiveGains(monthGains).add(unrealizedNet).subtract(losses(monthGains)).setScale(2, RoundingMode.HALF_UP));
        }
        return values;
    }

    private List<BigDecimal> flatSparkline(BigDecimal value) {
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return List.of(scaled, scaled, scaled, scaled, scaled, scaled);
    }

    private List<CumulativeGainPoint> cumulativeGains(
            List<InvestmentTransaction> transactions,
            List<HoldingGain> holdingGains,
            DateRange range,
            String currency) {
        List<YearMonth> months = chartMonths(range);
        BigDecimal cumulativeRealized = BigDecimal.ZERO;
        BigDecimal cumulativeLosses = BigDecimal.ZERO;
        BigDecimal currentUnrealized = positiveHoldingGains(holdingGains);
        List<CumulativeGainPoint> points = new ArrayList<>();

        for (YearMonth month : months) {
            List<InvestmentTransaction> monthTransactions = transactions.stream()
                    .filter(transaction -> transaction.getDate() != null)
                    .filter(transaction -> YearMonth.from(transaction.getDate()).equals(month))
                    .toList();
            List<TransactionGain> monthGains = sellGains(monthTransactions, transactions, currency);
            cumulativeRealized = cumulativeRealized.add(positiveGains(monthGains));
            cumulativeLosses = cumulativeLosses.add(losses(monthGains));
            points.add(new CumulativeGainPoint(
                    month.format(MONTH_LABEL_DATE),
                    cumulativeRealized.setScale(2, RoundingMode.HALF_UP),
                    currentUnrealized,
                    cumulativeLosses.negate().setScale(2, RoundingMode.HALF_UP)));
        }
        return points;
    }

    private List<YearMonth> chartMonths(DateRange range) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth start = YearMonth.from(range.from());
        YearMonth end = YearMonth.from(range.to());
        long distance = (end.getYear() - start.getYear()) * 12L + end.getMonthValue() - start.getMonthValue();
        if (distance < 5) {
            start = end.minusMonths(5);
        } else if (distance > 11) {
            start = end.minusMonths(11);
        }
        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    private List<TaxSnapshotItem> taxSnapshot(
            List<TransactionGain> gains,
            BigDecimal taxableGainEstimate,
            String currency,
            DateRange range) {
        BigDecimal realized = positiveGains(gains);
        BigDecimal capitalLosses = losses(gains);
        BigDecimal registered = gains.stream()
                .filter(gain -> isRegisteredAccount(gain.transaction().getAccountName()))
                .map(TransactionGain::proceeds)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal nonRegistered = gains.stream()
                .filter(gain -> !isRegisteredAccount(gain.transaction().getAccountName()))
                .map(TransactionGain::proceeds)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalActivity = registered.add(nonRegistered);

        return List.of(
                new TaxSnapshotItem("Realized Gain", money(realized, currency), percentLabel(realized, realized.add(capitalLosses))),
                new TaxSnapshotItem("Capital Losses", money(capitalLosses, currency), percentLabel(capitalLosses, realized.add(capitalLosses))),
                new TaxSnapshotItem("Registered Accounts", money(registered, currency), percentLabel(registered, totalActivity)),
                new TaxSnapshotItem("Non-Registered Accounts", money(nonRegistered, currency), percentLabel(nonRegistered, totalActivity)),
                new TaxSnapshotItem("Taxable Gain Estimate", money(taxableGainEstimate, currency), range.to().getYear() + " estimate"));
    }

    private List<Insight> insights(List<TransactionGain> gains, List<HoldingGain> holdingGains) {
        List<Insight> insights = new ArrayList<>();
        gains.stream()
                .filter(gain -> gain.gainLoss() != null)
                .max(Comparator.comparing(TransactionGain::gainLoss))
                .ifPresent(gain -> insights.add(new Insight(
                        "biggest-realized-gain",
                        "Biggest realized gain",
                        securityLabel(gain.transaction()),
                        money(gain.gainLoss(), gain.currency()),
                        "green",
                        "gain")));
        gains.stream()
                .filter(gain -> gain.gainLoss() != null)
                .min(Comparator.comparing(TransactionGain::gainLoss))
                .filter(gain -> gain.gainLoss().compareTo(BigDecimal.ZERO) < 0)
                .ifPresent(gain -> insights.add(new Insight(
                        "largest-loss",
                        "Largest loss",
                        securityLabel(gain.transaction()),
                        money(gain.gainLoss(), gain.currency()),
                        "rose",
                        "loss")));

        long taxableAccounts = gains.stream()
                .filter(gain -> !isRegisteredAccount(gain.transaction().getAccountName()))
                .map(gain -> fallback(gain.transaction().getAccountId(), gain.transaction().getAccountName()))
                .distinct()
                .count();
        insights.add(new Insight(
                "tax-relevant-accounts",
                "Accounts with tax-relevant activity",
                taxableAccounts == 0 ? "No taxable account sell activity in the selected period." : taxableAccounts + " account(s)",
                String.valueOf(taxableAccounts),
                taxableAccounts == 0 ? "info" : "violet",
                "scale"));

        long profitableHoldings = holdingGains.stream()
                .filter(holding -> holding.gainLoss().compareTo(BigDecimal.ZERO) > 0)
                .count();
        insights.add(new Insight(
                "profitable-open-positions",
                "Profitable open positions",
                profitableHoldings + " current holding(s) are above estimated cost basis.",
                profitableHoldings + "/" + holdingGains.size(),
                "green",
                "file"));
        return insights;
    }

    private List<ReviewItem> reviewItems(List<TransactionGain> gains) {
        List<ReviewItem> items = new ArrayList<>();
        long missingBasis = gains.stream().filter(TransactionGain::missingBasis).count();
        if (missingBasis > 0) {
            items.add(new ReviewItem(
                    "missing-cost-basis",
                    "Missing cost basis",
                    missingBasis + " sell transaction(s) need tax-lot or cost basis details.",
                    "high",
                    "orange",
                    "file"));
        }

        long losses = gains.stream()
                .map(TransactionGain::gainLoss)
                .filter(Objects::nonNull)
                .filter(value -> value.compareTo(BigDecimal.ZERO) < 0)
                .count();
        if (losses > 0) {
            items.add(new ReviewItem(
                    "superficial-loss-review",
                    "Superficial loss review",
                    losses + " realized loss transaction(s) should be checked for Canadian superficial loss rules.",
                    "medium",
                    "violet",
                    "scale"));
        }

        if (!gains.isEmpty()) {
            items.add(new ReviewItem(
                    "verify-before-filing",
                    "Gains to verify before filing",
                    gains.size() + " sell transaction(s) should be reviewed before tax filing.",
                    "medium",
                    "indigo",
                    "file"));
        }
        return items;
    }

    private List<ActivityRow> activityRows(List<TransactionGain> gains, String fallbackCurrency) {
        return gains.stream()
                .map(gain -> {
                    InvestmentTransaction transaction = gain.transaction();
                    String currency = fallback(transaction.getCurrency(), fallbackCurrency);
                    return new ActivityRow(
                            transactionId(transaction),
                            transaction.getDate() == null ? "" : transaction.getDate().toString(),
                            fallback(transaction.getSecurityName(), "Investment"),
                            fallback(transaction.getTicker(), ""),
                            fallback(transaction.getAccountName(), "Investment account"),
                            gain.proceeds(),
                            gain.costBasis(),
                            gain.gainLoss(),
                            currency,
                            gain.missingBasis() ? "Cost basis missing" : "Realized",
                            gain.missingBasis() ? "Needs basis" : investmentStatusPolicy.determineInvestmentTransactionStatus(transaction),
                            logoText(fallback(transaction.getTicker(), transaction.getSecurityName())),
                            null);
                })
                .toList();
    }

    private boolean sameHolding(InvestmentTransaction left, InvestmentTransaction right) {
        if (!Objects.equals(left.getAccountId(), right.getAccountId())) {
            return false;
        }
        String leftSecurity = holdingKey(left.getSecurityId(), left.getTicker(), left.getSecurityName());
        String rightSecurity = holdingKey(right.getSecurityId(), right.getTicker(), right.getSecurityName());
        return Objects.equals(leftSecurity, rightSecurity);
    }

    private boolean sameSecurity(InvestmentTransaction transaction, InvestmentHolding holding) {
        return Objects.equals(
                holdingKey(transaction.getSecurityId(), transaction.getTicker(), transaction.getSecurityName()),
                holdingKey(holding.getSecurityId(), holding.getTicker(), holding.getSecurityName()));
    }

    private String holdingKey(String securityId, String ticker, String name) {
        return fallback(securityId, fallback(ticker, fallback(name, ""))).trim().toLowerCase(Locale.US);
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

    private boolean isCashLike(HoldingContext context) {
        String value = String.join(" ",
                fallback(context.holding().getSecurityName(), ""),
                context.security() == null ? "" : fallback(context.security().getType(), ""),
                context.security() == null ? "" : fallback(context.security().getSubtype(), ""))
                .toLowerCase(Locale.US);
        return value.contains("cash") || value.contains("money market");
    }

    private boolean isRegisteredAccount(String accountName) {
        String value = fallback(accountName, "").toLowerCase(Locale.US);
        return value.contains("tfsa")
                || value.contains("rrsp")
                || value.contains("resp")
                || value.contains("fhsa")
                || value.contains("registered");
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

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private String percentLabel(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return "0.0%";
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator.abs(), 1, RoundingMode.HALF_UP)
                .toPlainString() + "%";
    }

    private String money(BigDecimal amount, String currency) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        return currency + " " + value.toPlainString();
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String securityLabel(InvestmentTransaction transaction) {
        String ticker = fallback(transaction.getTicker(), "");
        String name = fallback(transaction.getSecurityName(), "Investment");
        return ticker.isBlank() ? name : name + " (" + ticker + ")";
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

    private String logoText(String value) {
        String cleaned = fallback(value, "cg").replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isBlank()) {
            return "cg";
        }
        return cleaned.length() <= 3
                ? cleaned.toLowerCase(Locale.US)
                : cleaned.substring(0, 3).toLowerCase(Locale.US);
    }

    private enum MetricMode {
        REALIZED_GAIN,
        LOSS
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }

    private record HoldingContext(PlaidItem plaidItem, InvestmentHolding holding, InvestmentSecurity security) {
    }

    private record HoldingGain(
            InvestmentHolding holding,
            BigDecimal marketValue,
            BigDecimal costBasis,
            BigDecimal gainLoss,
            String currency) {
    }

    private record TransactionGain(
            InvestmentTransaction transaction,
            BigDecimal proceeds,
            BigDecimal costBasis,
            BigDecimal gainLoss,
            boolean missingBasis,
            String currency) {
    }
}
