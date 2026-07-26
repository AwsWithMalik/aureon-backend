package com.Accounting.app.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.Accounting.app.accounts.Account;
import com.Accounting.app.accounts.AccountRepo;
import com.Accounting.app.dashboard.dto.TransactionUpdateRequest;
import com.Accounting.app.dashboard.dto.TransactionsPageResponse;
import com.Accounting.app.dashboard.dto.TransactionsPageResponse.AccountFilter;
import com.Accounting.app.dashboard.dto.TransactionsPageResponse.Filters;
import com.Accounting.app.dashboard.dto.TransactionsPageResponse.Money;
import com.Accounting.app.dashboard.dto.TransactionsPageResponse.Pagination;
import com.Accounting.app.dashboard.dto.TransactionsPageResponse.Summary;
import com.Accounting.app.dashboard.dto.TransactionsPageResponse.TransactionRow;
import com.Accounting.app.files.FileRepo;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.transactions.TransactionType;
import com.Accounting.app.transactions.TransactionsRepo;

@Service
public class TransactionsPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final DateTimeFormatter FULL_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT_LABEL_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private final AccountRepo accountRepo;
    private final TransactionsRepo transactionsRepo;
    private final FileRepo fileRepo;
    private final MerchantLogoUrlService merchantLogoUrlService;

    public TransactionsPageServices(
            AccountRepo accountRepo,
            TransactionsRepo transactionsRepo,
            FileRepo fileRepo,
            MerchantLogoUrlService merchantLogoUrlService) {
        this.accountRepo = accountRepo;
        this.transactionsRepo = transactionsRepo;
        this.fileRepo = fileRepo;
        this.merchantLogoUrlService = merchantLogoUrlService;
    }

    public TransactionsPageResponse transactionsPageResponse(
            String email,
            LocalDate from,
            LocalDate to,
            String accountScope,
            int page,
            int pageSize,
            boolean showAll) {
        List<Account> accounts = scopedAccounts(accountRepo.findAllByEmail(email), accountScope);
        List<Transaction> scopedTransactions = accounts.stream()
                .flatMap(account -> transactionsRepo.findByAccountId(account.getId()).stream())
                .toList();

        DateRange range = normalizeRange(scopedTransactions, from, to, showAll);
        DateRange previousRange = previousRange(range, showAll);
        int normalizedPage = Math.max(1, page);
        int normalizedPageSize = Math.max(1, Math.min(500, pageSize));

        List<Transaction> rangeTransactions = transactionsForRange(scopedTransactions, range).stream()
                .sorted(Comparator.comparing(Transaction::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Transaction> previousTransactions = showAll ? List.of() : transactionsForRange(scopedTransactions, previousRange);

        int fromIndex = Math.min((normalizedPage - 1) * normalizedPageSize, rangeTransactions.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, rangeTransactions.size());
        List<TransactionRow> rows = rangeTransactions.subList(fromIndex, toIndex).stream()
                .map(transaction -> toRow(transaction, email))
                .toList();

        int totalPages = rangeTransactions.isEmpty()
                ? 0
                : (int) Math.ceil((double) rangeTransactions.size() / normalizedPageSize);

        return new TransactionsPageResponse(
                periodLabel(range, showAll),
                accountLabel(accountScope),
                summary(rangeTransactions, previousTransactions),
                filters(accounts, rangeTransactions),
                rows,
                new Pagination(normalizedPage, normalizedPageSize, rangeTransactions.size(), totalPages));
    }

    public TransactionRow updateTransaction(String email, Integer transactionId, TransactionUpdateRequest request) {
        Transaction transaction = transactionsRepo.findByTransactionIdAndAccount_User_Email(transactionId, email)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (request.category() != null) {
            transaction.setDisplayCategory(clean(request.category()));
        }

        List<String> metadata = transaction.getMetadata() == null
                ? new ArrayList<>()
                : new ArrayList<>(transaction.getMetadata());

        if (request.notes() != null) {
            putMetadataValue(metadata, "notes", clean(request.notes()));
        }

        if (request.includedInCashFlow() != null) {
            putMetadataValue(metadata, "includedInCashFlow", String.valueOf(request.includedInCashFlow()));
        }

        transaction.setMetadata(metadata);
        Transaction saved = transactionsRepo.save(transaction);
        return toRow(saved, email);
    }

    private Summary summary(List<Transaction> transactions, List<Transaction> previousTransactions) {
        BigDecimal inflow = totalByType(transactions, TransactionType.INCOME);
        BigDecimal outflow = totalByType(transactions, TransactionType.EXPENSE);
        BigDecimal netCashFlow = inflow.subtract(outflow);

        BigDecimal previousInflow = totalByType(previousTransactions, TransactionType.INCOME);
        BigDecimal previousOutflow = totalByType(previousTransactions, TransactionType.EXPENSE);
        BigDecimal previousNetCashFlow = previousInflow.subtract(previousOutflow);

        return new Summary(
                transactions.size(),
                percentChange(BigDecimal.valueOf(transactions.size()), BigDecimal.valueOf(previousTransactions.size())),
                money(inflow),
                percentChange(inflow, previousInflow),
                money(outflow),
                percentChange(outflow, previousOutflow),
                money(netCashFlow),
                percentChange(netCashFlow, previousNetCashFlow),
                transactions.stream().filter(transaction -> "to_review".equals(status(transaction))).count());
    }

    private Filters filters(List<Account> accounts, List<Transaction> transactions) {
        Set<String> categories = new LinkedHashSet<>();
        transactions.stream()
                .map(Transaction::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .forEach(categories::add);

        List<AccountFilter> accountFilters = accounts.stream()
                .map(account -> new AccountFilter(
                        stableAccountId(account),
                        fallback(account.getAccountName(), "Account"),
                        cleanMask(account.getMask())))
                .toList();

        return new Filters(
                List.copyOf(categories),
                accountFilters,
                List.of("income", "expense", "transfer"),
                List.of("categorized", "to_review", "pending", "excluded"));
    }

    private TransactionRow toRow(Transaction transaction, String email) {
        Account account = transaction.getAccount();
        String id = stableTransactionId(transaction);
        String type = type(transaction);
        String status = status(transaction);
        String category = fallback(transaction.getDisplayCategory(), "Uncategorized");

        return new TransactionRow(
                id,
                transaction.getTimestamp() == null ? "" : transaction.getTimestamp().toLocalDate().toString(),
                fallback(transaction.getMerchantName(), fallback(transaction.getDescription(), "Transaction")),
                fallback(transaction.getDescription(), fallback(transaction.getMerchantName(), "Transaction")),
                account == null ? "" : stableAccountId(account),
                account == null ? "Account" : fallback(account.getAccountName(), "Account"),
                account == null ? "" : cleanMask(account.getMask()),
                category,
                type,
                money(displayAmount(transaction)),
                status,
                logoText(fallback(transaction.getMerchantName(), transaction.getDescription())),
                merchantLogoUrlService.toClientLogoUrl(metadataValue(transaction, "logoUrl")),
                metadataValue(transaction, "notes"),
                transaction.getId() == null ? 0
                        : fileRepo.countByRelatedTransaction_TransactionIdAndUser_Email(transaction.getId(), email),
                includedInCashFlow(transaction),
                transaction.getId() == null ? "" : String.valueOf(transaction.getId()),
                transaction.getPlaidTransactionId());
    }

    private List<Transaction> transactionsForRange(List<Transaction> transactions, DateRange range) {
        return transactions.stream()
                .filter(transaction -> transaction.getTimestamp() != null)
                .filter(transaction -> {
                    LocalDate date = transaction.getTimestamp().toLocalDate();
                    return !date.isBefore(range.from()) && !date.isAfter(range.to());
                })
                .toList();
    }

    private List<Account> scopedAccounts(List<Account> accounts, String accountScope) {
        return accounts.stream()
                .filter(account -> matchesAccountScope(account, accountScope))
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

    private BigDecimal displayAmount(Transaction transaction) {
        BigDecimal amount = transaction.getAmount() == null ? BigDecimal.ZERO : transaction.getAmount().abs();
        if (transaction.getType() == TransactionType.EXPENSE) {
            return amount.negate();
        }
        return amount;
    }

    private String status(Transaction transaction) {
        return determineTransactionStatus(transaction);
    }

    private String determineTransactionStatus(Transaction transaction) {
        if (isExcluded(transaction)) {
            return "excluded";
        }
        if (isPending(transaction)) {
            return "pending";
        }
        if (needsReview(transaction)) {
            return "to_review";
        }
        return "categorized";
    }

    private boolean isExcluded(Transaction transaction) {
        return transaction.getType() == TransactionType.TRANSFER || !includedInCashFlow(transaction);
    }

    private boolean isPending(Transaction transaction) {
        return transaction.isPending();
    }

    private boolean needsReview(Transaction transaction) {
        if (transaction.getType() == null) {
            return true;
        }

        String category = transaction.getDisplayCategory();
        if (category == null || category.isBlank() || "uncategorized".equalsIgnoreCase(category)) {
            return true;
        }

        if (missingMerchantAndDescription(transaction)) {
            return true;
        }

        return transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean missingMerchantAndDescription(Transaction transaction) {
        String merchant = clean(transaction.getMerchantName());
        String description = clean(transaction.getDescription());
        return merchant == null && description == null;
    }

    private boolean includedInCashFlow(Transaction transaction) {
        String value = metadataValue(transaction, "includedInCashFlow");
        if (value == null || value.isBlank()) {
            return transaction.getType() != TransactionType.TRANSFER;
        }
        return Boolean.parseBoolean(value);
    }

    private String type(Transaction transaction) {
        if (transaction.getType() == null) {
            return "expense";
        }
        return transaction.getType().name().toLowerCase(Locale.US);
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

    private DateRange normalizeRange(List<Transaction> transactions, LocalDate from, LocalDate to, boolean showAll) {
        if (showAll) {
            LocalDate earliest = transactions.stream()
                    .map(Transaction::getTimestamp)
                    .filter(value -> value != null)
                    .map(LocalDateTime::toLocalDate)
                    .min(Comparator.naturalOrder())
                    .orElse(LocalDate.now());
            LocalDate latest = transactions.stream()
                    .map(Transaction::getTimestamp)
                    .filter(value -> value != null)
                    .map(LocalDateTime::toLocalDate)
                    .max(Comparator.naturalOrder())
                    .orElse(LocalDate.now());
            return earliest.isAfter(latest)
                    ? new DateRange(latest, earliest)
                    : new DateRange(earliest, latest);
        }

        LocalDate normalizedFrom = from;
        LocalDate normalizedTo = to;

        if (normalizedFrom == null && normalizedTo == null) {
            YearMonth month = latestMonth(transactions);
            normalizedFrom = month.atDay(1);
            normalizedTo = month.atEndOfMonth();
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

    private DateRange previousRange(DateRange range, boolean showAll) {
        if (showAll) {
            return range;
        }
        YearMonth month = YearMonth.from(range.from());
        if (range.from().equals(month.atDay(1)) && range.to().equals(month.atEndOfMonth())) {
            YearMonth previousMonth = month.minusMonths(1);
            return new DateRange(previousMonth.atDay(1), previousMonth.atEndOfMonth());
        }

        long days = range.to().toEpochDay() - range.from().toEpochDay();
        LocalDate previousTo = range.from().minusDays(1);
        return new DateRange(previousTo.minusDays(days), previousTo);
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

    private String periodLabel(DateRange range, boolean showAll) {
        if (showAll) {
            return "All transactions";
        }
        return range.from().format(SHORT_LABEL_DATE) + " - " + range.to().format(FULL_LABEL_DATE);
    }

    private String accountLabel(String accountScope) {
        return accountScope == null || accountScope.isBlank() || "all".equalsIgnoreCase(accountScope)
                ? "All accounts"
                : "Selected account";
    }

    private boolean matchesAccountScope(Account account, String accountScope) {
        if (accountScope == null || accountScope.isBlank() || "all".equalsIgnoreCase(accountScope)) {
            return true;
        }
        return accountScope.equals(account.getAccountId())
                || accountScope.equals(account.getPlaidAccountId())
                || accountScope.equals(account.getId() == null ? "" : account.getId().toString());
    }

    private YearMonth latestMonth(List<Transaction> transactions) {
        LocalDate latest = transactions.stream()
                .map(Transaction::getTimestamp)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .max(Comparator.naturalOrder())
                .orElse(LocalDate.now());
        return YearMonth.from(latest);
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

    private String cleanMask(String mask) {
        if (mask == null || mask.isBlank()) {
            return "";
        }
        return mask.replace("*", "").trim();
    }

    private String logoText(String value) {
        if (value == null || value.isBlank()) {
            return "tx";
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isBlank()) {
            return "tx";
        }
        return cleaned.length() <= 3 ? cleaned.toLowerCase(Locale.US) : cleaned.substring(0, 3).toLowerCase(Locale.US);
    }

    private Money money(BigDecimal amount) {
        return new Money(amount == null ? BigDecimal.ZERO : amount, DEFAULT_CURRENCY);
    }

    private void putMetadataValue(List<String> metadata, String key, String value) {
        String prefix = key + "=";
        metadata.removeIf(entry -> entry != null && entry.startsWith(prefix));
        if (value != null && !value.isBlank()) {
            metadata.add(prefix + value);
        }
    }

    private String clean(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
