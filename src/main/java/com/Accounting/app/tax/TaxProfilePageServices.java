package com.Accounting.app.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.accounts.Account;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.transactions.TransactionType;
import com.Accounting.app.auth.User;
import com.Accounting.app.tax.dto.TaxProfilePageResponse;
import com.Accounting.app.accounts.dto.Balance;
import com.Accounting.app.tax.dto.DataSource;
import com.Accounting.app.tax.dto.Deadline;
import com.Accounting.app.tax.dto.DeductionCategory;
import com.Accounting.app.tax.dto.EstimatedPayment;
import com.Accounting.app.tax.dto.FilingProfile;
import com.Accounting.app.tax.dto.IncomeSource;
import com.Accounting.app.tax.dto.Jurisdiction;
import com.Accounting.app.accounts.AccountRepo;
import com.Accounting.app.transactions.TransactionsRepo;
import com.Accounting.app.auth.UserRepo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TaxProfilePageServices {
    private static final String DEFAULT_CURRENCY = "CAD";

    private final UserRepo userRepo;
    private final TaxProfileRepo taxProfileRepo;
    private final TaxProfileConfigRepo taxProfileConfigRepo;
    private final AccountRepo accountRepo;
    private final TransactionsRepo transactionsRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TaxProfilePageServices(
            UserRepo userRepo,
            TaxProfileRepo taxProfileRepo,
            TaxProfileConfigRepo taxProfileConfigRepo,
            AccountRepo accountRepo,
            TransactionsRepo transactionsRepo) {
        this.userRepo = userRepo;
        this.taxProfileRepo = taxProfileRepo;
        this.taxProfileConfigRepo = taxProfileConfigRepo;
        this.accountRepo = accountRepo;
        this.transactionsRepo = transactionsRepo;
    }

    @Transactional
    public TaxProfilePageResponse taxProfilePageResponse(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        TaxProfilePageResponse defaults = defaultTaxProfilePageResponse(email, user);
        TaxProfileConfig config = taxProfileConfigRepo.findByEmail(email).orElse(null);

        if (config == null) {
            return defaults;
        }

        return toResponse(config);
    }

    @Transactional
    public TaxProfilePageResponse updateTaxProfile(String email, TaxProfilePageResponse request) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        TaxProfilePageResponse defaults = defaultTaxProfilePageResponse(email, user);
        TaxProfilePageResponse safeRequest = mergeWithDefaults(request, defaults);
        TaxProfileConfig savedConfig = taxProfileConfigRepo.save(toConfig(email, safeRequest));

        return toResponse(savedConfig);
    }

    @Transactional
    public Map<String, Object> taxProfilePagePayload(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        TaxProfilePageResponse defaults = defaultTaxProfilePageResponse(email, user);
        TaxProfileConfig config = taxProfileConfigRepo.findByEmail(email).orElse(null);

        if (config == null) {
            return typedPayload(defaults);
        }

        return rawOrTypedPayload(config);
    }

    @Transactional
    public Map<String, Object> updateTaxProfilePayload(String email, Map<String, Object> request) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        TaxProfilePageResponse defaults = defaultTaxProfilePageResponse(email, user);

        TaxProfileConfig config = taxProfileConfigRepo.findByEmail(email)
                .orElseGet(() -> toConfig(email, defaults));
        if (isLegacyTaxProfilePayload(request)) {
            config = toConfig(email, mergeWithDefaults(toTypedTaxProfile(request), defaults));
        }
        config.setRawConfigJson(toJson(request));

        return rawOrTypedPayload(taxProfileConfigRepo.save(config));
    }

    private TaxProfilePageResponse defaultTaxProfilePageResponse(String email, User user) {
        Optional<TaxProfile> taxProfile = taxProfileRepo.findAllByEmail(email).stream().findFirst();
        List<Account> accounts = accountRepo.findAllByEmail(email);
        List<Transaction> transactions = getTransactionsForAccounts(accounts);
        int taxYear = LocalDate.now().getYear();

        return new TaxProfilePageResponse(
                buildFilingProfile(user, taxProfile, taxYear),
                buildJurisdictions(taxProfile),
                buildIncomeSources(transactions),
                buildDeductionCategories(transactions),
                buildEstimatedPayments(taxYear),
                buildDeadlines(taxYear),
                buildDataSources(accounts));
    }

    private TaxProfilePageResponse mergeWithDefaults(TaxProfilePageResponse request, TaxProfilePageResponse defaults) {
        if (request == null) {
            return defaults;
        }

        return new TaxProfilePageResponse(
                request.getFilingProfile() != null ? request.getFilingProfile() : defaults.getFilingProfile(),
                request.getJurisdictions() != null ? request.getJurisdictions() : defaults.getJurisdictions(),
                request.getIncomeSources() != null ? request.getIncomeSources() : defaults.getIncomeSources(),
                request.getDeductionCategories() != null ? request.getDeductionCategories() : defaults.getDeductionCategories(),
                request.getEstimatedPayments() != null ? request.getEstimatedPayments() : defaults.getEstimatedPayments(),
                request.getDeadlines() != null ? request.getDeadlines() : defaults.getDeadlines(),
                request.getDataSources() != null ? request.getDataSources() : defaults.getDataSources());
    }

    private boolean isLegacyTaxProfilePayload(Map<String, Object> request) {
        return request != null
                && (request.containsKey("filingProfile")
                        || request.containsKey("jurisdictions")
                        || request.containsKey("incomeSources")
                        || request.containsKey("deductionCategories")
                        || request.containsKey("estimatedPayments")
                        || request.containsKey("deadlines")
                        || request.containsKey("dataSources"));
    }

    private TaxProfilePageResponse toTypedTaxProfile(Map<String, Object> request) {
        try {
            return objectMapper.convertValue(request, TaxProfilePageResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> rawOrTypedPayload(TaxProfileConfig config) {
        if (config.getRawConfigJson() != null && !config.getRawConfigJson().isBlank()) {
            try {
                return objectMapper.readValue(config.getRawConfigJson(), new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception ex) {
                // Fall back to the typed projection if the stored snapshot is malformed.
            }
        }
        return typedPayload(toResponse(config));
    }

    private String toJson(Map<String, Object> request) {
        if (request == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> typedPayload(TaxProfilePageResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        FilingProfile filingProfile = response.getFilingProfile();
        Map<String, Object> filingProfilePayload = new LinkedHashMap<>();
        filingProfilePayload.put("legalName", filingProfile.getLegalName());
        filingProfilePayload.put("filingStatus", filingProfile.getFilingStatus());
        filingProfilePayload.put("entityType", filingProfile.getEntityType());
        filingProfilePayload.put("taxResidence", filingProfile.getTaxResidence());
        filingProfilePayload.put("taxYear", filingProfile.getTaxYear());
        filingProfilePayload.put("taxYearStartMonth", filingProfile.getTaxYearStartMonth());
        filingProfilePayload.put("baseCurrency", filingProfile.getBaseCurrency());
        payload.put("filingProfile", filingProfilePayload);

        payload.put("jurisdictions", response.getJurisdictions().stream()
                .map(item -> {
                    Map<String, Object> itemPayload = new LinkedHashMap<>();
                    itemPayload.put("id", item.getId());
                    itemPayload.put("name", item.getName());
                    itemPayload.put("type", item.getType());
                    itemPayload.put("registrationNumber", item.getRegistrationNumber());
                    itemPayload.put("status", item.getStatus());
                    return itemPayload;
                })
                .toList());

        payload.put("incomeSources", response.getIncomeSources().stream()
                .map(item -> {
                    Map<String, Object> itemPayload = new LinkedHashMap<>();
                    itemPayload.put("id", item.getId());
                    itemPayload.put("label", item.getLabel());
                    itemPayload.put("category", item.getCategory());
                    itemPayload.put("yearToDate", moneyPayload(item.getYearToDate()));
                    itemPayload.put("taxWithheld", item.getTaxWithheld() == null ? null : moneyPayload(item.getTaxWithheld()));
                    return itemPayload;
                })
                .toList());

        payload.put("deductionCategories", response.getDeductionCategories().stream()
                .map(item -> {
                    Map<String, Object> itemPayload = new LinkedHashMap<>();
                    itemPayload.put("id", item.getId());
                    itemPayload.put("name", item.getName());
                    itemPayload.put("trackedAmount", moneyPayload(item.getTrackedAmount()));
                    itemPayload.put("documentationStatus", item.getDocumentationStatus());
                    return itemPayload;
                })
                .toList());

        payload.put("estimatedPayments", response.getEstimatedPayments().stream()
                .map(item -> {
                    Map<String, Object> itemPayload = new LinkedHashMap<>();
                    itemPayload.put("id", item.getId());
                    itemPayload.put("dueDate", item.getDueDate() == null ? null : item.getDueDate().toString());
                    itemPayload.put("amount", moneyPayload(item.getAmount()));
                    itemPayload.put("status", item.getStatus());
                    return itemPayload;
                })
                .toList());

        payload.put("deadlines", response.getDeadlines().stream()
                .map(item -> {
                    Map<String, Object> itemPayload = new LinkedHashMap<>();
                    itemPayload.put("id", item.getId());
                    itemPayload.put("label", item.getLabel());
                    itemPayload.put("dueDate", item.getDueDate() == null ? null : item.getDueDate().toString());
                    itemPayload.put("status", item.getStatus());
                    return itemPayload;
                })
                .toList());

        payload.put("dataSources", response.getDataSources().stream()
                .map(item -> {
                    Map<String, Object> itemPayload = new LinkedHashMap<>();
                    itemPayload.put("id", item.getId());
                    itemPayload.put("label", item.getLabel());
                    itemPayload.put("sourceType", item.getSourceType());
                    itemPayload.put("lastSyncedAt", item.getLastSyncedAt() == null ? null : item.getLastSyncedAt().toString());
                    itemPayload.put("status", item.getStatus());
                    return itemPayload;
                })
                .toList());

        return payload;
    }

    private Map<String, Object> moneyPayload(Balance balance) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", balance == null ? BigDecimal.ZERO : defaultAmount(balance.getAmount()));
        payload.put("currency", balance == null ? DEFAULT_CURRENCY : normalizeCurrency(balance.getCurrency()));
        return payload;
    }

    private TaxProfileConfig toConfig(String email, TaxProfilePageResponse response) {
        TaxProfileConfig config = new TaxProfileConfig();
        FilingProfile filingProfile = response.getFilingProfile();

        config.setEmail(email);
        config.setLegalName(clean(filingProfile.getLegalName()));
        config.setFilingStatus(clean(filingProfile.getFilingStatus()));
        config.setEntityType(clean(filingProfile.getEntityType()));
        config.setTaxResidence(clean(filingProfile.getTaxResidence()));
        config.setTaxYear(filingProfile.getTaxYear());
        config.setTaxYearStartMonth(filingProfile.getTaxYearStartMonth());
        config.setBaseCurrency(normalizeCurrency(filingProfile.getBaseCurrency()));
        config.setJurisdictions(response.getJurisdictions().stream()
                .map(item -> new TaxProfileJurisdictionConfig(clean(item.getId()), clean(item.getName()), clean(item.getType()),
                        clean(item.getRegistrationNumber()), clean(item.getStatus())))
                .toList());
        config.setIncomeSources(response.getIncomeSources().stream()
                .map(item -> new TaxProfileIncomeSourceConfig(
                        clean(item.getId()),
                        clean(item.getLabel()),
                        clean(item.getCategory()),
                        item.getYearToDate() != null ? defaultAmount(item.getYearToDate().getAmount()) : BigDecimal.ZERO,
                        item.getYearToDate() != null ? normalizeCurrency(item.getYearToDate().getCurrency()) : DEFAULT_CURRENCY,
                        item.getTaxWithheld() != null ? defaultAmount(item.getTaxWithheld().getAmount()) : null,
                        item.getTaxWithheld() != null ? normalizeCurrency(item.getTaxWithheld().getCurrency()) : null))
                .toList());
        config.setDeductionCategories(response.getDeductionCategories().stream()
                .map(item -> new TaxProfileDeductionCategoryConfig(
                        clean(item.getId()),
                        clean(item.getName()),
                        item.getTrackedAmount() != null ? defaultAmount(item.getTrackedAmount().getAmount()) : BigDecimal.ZERO,
                        item.getTrackedAmount() != null ? normalizeCurrency(item.getTrackedAmount().getCurrency()) : DEFAULT_CURRENCY,
                        clean(item.getDocumentationStatus())))
                .toList());
        config.setEstimatedPayments(response.getEstimatedPayments().stream()
                .map(item -> new TaxProfileEstimatedPaymentConfig(
                        clean(item.getId()),
                        item.getDueDate(),
                        item.getAmount() != null ? defaultAmount(item.getAmount().getAmount()) : BigDecimal.ZERO,
                        item.getAmount() != null ? normalizeCurrency(item.getAmount().getCurrency()) : DEFAULT_CURRENCY,
                        clean(item.getStatus())))
                .toList());
        config.setDeadlines(response.getDeadlines().stream()
                .map(item -> new TaxProfileDeadlineConfig(clean(item.getId()), clean(item.getLabel()), item.getDueDate(),
                        clean(item.getStatus())))
                .toList());
        config.setDataSources(response.getDataSources().stream()
                .map(item -> new TaxProfileDataSourceConfig(clean(item.getId()), clean(item.getLabel()), clean(item.getSourceType()),
                        item.getLastSyncedAt(), clean(item.getStatus())))
                .toList());

        return config;
    }

    private TaxProfilePageResponse toResponse(TaxProfileConfig config) {
        FilingProfile filingProfile = new FilingProfile(
                fallback(config.getLegalName(), config.getEmail()),
                fallback(config.getFilingStatus(), "not_configured"),
                fallback(config.getEntityType(), "individual"),
                fallback(config.getTaxResidence(), "Canada"),
                config.getTaxYear() != null ? config.getTaxYear() : LocalDate.now().getYear(),
                config.getTaxYearStartMonth() != null ? config.getTaxYearStartMonth() : 1,
                normalizeCurrency(config.getBaseCurrency()));

        return new TaxProfilePageResponse(
                filingProfile,
                config.getJurisdictions().stream()
                        .map(item -> new Jurisdiction(item.getJurisdictionId(), item.getName(), item.getType(),
                                item.getRegistrationNumber(), item.getStatus()))
                        .toList(),
                config.getIncomeSources().stream()
                        .map(item -> new IncomeSource(
                                item.getSourceId(),
                                item.getLabel(),
                                item.getCategory(),
                                new Balance(defaultAmount(item.getYearToDateAmount()), normalizeCurrency(item.getYearToDateCurrency())),
                                item.getTaxWithheldAmount() != null
                                        ? new Balance(item.getTaxWithheldAmount(), normalizeCurrency(item.getTaxWithheldCurrency()))
                                        : null))
                        .toList(),
                config.getDeductionCategories().stream()
                        .map(item -> new DeductionCategory(
                                item.getCategoryId(),
                                item.getName(),
                                new Balance(defaultAmount(item.getTrackedAmount()), normalizeCurrency(item.getTrackedCurrency())),
                                item.getDocumentationStatus()))
                        .toList(),
                config.getEstimatedPayments().stream()
                        .map(item -> new EstimatedPayment(
                                item.getPaymentId(),
                                item.getDueDate(),
                                new Balance(defaultAmount(item.getAmount()), normalizeCurrency(item.getCurrency())),
                                item.getStatus()))
                        .toList(),
                config.getDeadlines().stream()
                        .map(item -> new Deadline(item.getDeadlineId(), item.getLabel(), item.getDueDate(), item.getStatus()))
                        .toList(),
                config.getDataSources().stream()
                        .map(item -> new DataSource(item.getSourceId(), item.getLabel(), item.getSourceType(), item.getLastSyncedAt(),
                                item.getStatus()))
                        .toList());
    }

    private FilingProfile buildFilingProfile(User user, Optional<TaxProfile> taxProfile, int taxYear) {
        String residence = taxProfile
                .map(profile -> String.join(", ",
                        List.of(fallback(profile.getCity(), ""), fallback(profile.getProvince(), ""),
                                fallback(profile.getCountry(), "")).stream()
                                .filter(value -> !value.isBlank())
                                .toList()))
                .filter(value -> !value.isBlank())
                .orElse("Canada");

        return new FilingProfile(
                fallback(user.getName(), user.getEmail()),
                "not_configured",
                taxProfile.map(TaxProfile::getIncomeType).filter(value -> !value.isBlank()).orElse("individual"),
                residence,
                taxYear,
                1,
                DEFAULT_CURRENCY);
    }

    private List<Jurisdiction> buildJurisdictions(Optional<TaxProfile> taxProfile) {
        if (taxProfile.isEmpty()) {
            return List.of(new Jurisdiction("canada", "Canada", "federal", null, "active"));
        }

        TaxProfile profile = taxProfile.get();
        String country = fallback(profile.getCountry(), "Canada");
        String province = fallback(profile.getProvince(), "");

        if (province.isBlank()) {
            return List.of(new Jurisdiction(toId(country), country, "federal", null, "active"));
        }

        return List.of(
                new Jurisdiction(toId(country), country, "federal", null, "active"),
                new Jurisdiction(toId(province), province, "provincial", null, "active"));
    }

    private List<IncomeSource> buildIncomeSources(List<Transaction> transactions) {
        Map<String, BigDecimal> incomeByCategory = groupByCategory(transactions, TransactionType.INCOME);

        return incomeByCategory.entrySet().stream()
                .map(entry -> new IncomeSource(
                        toId(entry.getKey()),
                        entry.getKey(),
                        entry.getKey(),
                        new Balance(entry.getValue(), DEFAULT_CURRENCY),
                        null))
                .toList();
    }

    private List<DeductionCategory> buildDeductionCategories(List<Transaction> transactions) {
        Map<String, BigDecimal> expensesByCategory = groupByCategory(transactions, TransactionType.EXPENSE);

        return expensesByCategory.entrySet().stream()
                .map(entry -> new DeductionCategory(
                        toId(entry.getKey()),
                        entry.getKey(),
                        new Balance(entry.getValue(), DEFAULT_CURRENCY),
                        "missing"))
                .toList();
    }

    private List<EstimatedPayment> buildEstimatedPayments(int taxYear) {
        return List.of(
                new EstimatedPayment("q1", LocalDate.of(taxYear, 3, 15), zeroMoney(), paymentStatus(taxYear, 3, 15)),
                new EstimatedPayment("q2", LocalDate.of(taxYear, 6, 15), zeroMoney(), paymentStatus(taxYear, 6, 15)),
                new EstimatedPayment("q3", LocalDate.of(taxYear, 9, 15), zeroMoney(), paymentStatus(taxYear, 9, 15)),
                new EstimatedPayment("q4", LocalDate.of(taxYear, 12, 15), zeroMoney(), paymentStatus(taxYear, 12, 15)));
    }

    private List<Deadline> buildDeadlines(int taxYear) {
        return List.of(
                new Deadline("filing-deadline", "Tax filing deadline", LocalDate.of(taxYear + 1, 4, 30),
                        deadlineStatus(taxYear + 1, 4, 30)),
                new Deadline("payment-deadline", "Balance payment deadline", LocalDate.of(taxYear + 1, 4, 30),
                        deadlineStatus(taxYear + 1, 4, 30)));
    }

    private List<DataSource> buildDataSources(List<Account> accounts) {
        return accounts.stream()
                .map(account -> new DataSource(
                        getStableAccountId(account),
                        fallback(account.getAccountName(), "Bank account"),
                        "bank",
                        getLastSyncedAt(account),
                        "connected"))
                .toList();
    }

    private List<Transaction> getTransactionsForAccounts(List<Account> accounts) {
        return accounts.stream()
                .filter(account -> account.getId() != null)
                .flatMap(account -> transactionsRepo.findByAccountId(account.getId()).stream())
                .toList();
    }

    private Map<String, BigDecimal> groupByCategory(List<Transaction> transactions, TransactionType type) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();

        transactions.stream()
                .filter(transaction -> transaction.getType() == type)
                .forEach(transaction -> totals.merge(
                        fallback(transaction.getDisplayCategory(), "Uncategorized"),
                        normalizeAmount(transaction.getAmount()),
                        BigDecimal::add));

        return totals;
    }

    private LocalDateTime getLastSyncedAt(Account account) {
        LocalDateTime transactionSync = account.getId() == null
                ? null
                : transactionsRepo.findByAccountId(account.getId()).stream()
                        .map(Transaction::getTimestamp)
                        .filter(timestamp -> timestamp != null)
                        .max(Comparator.naturalOrder())
                        .orElse(null);

        if (transactionSync != null) {
            return transactionSync;
        }

        return account.getDateAdded() != null ? account.getDateAdded() : account.getCreatedAt();
    }

    private Balance zeroMoney() {
        return new Balance(BigDecimal.ZERO, DEFAULT_CURRENCY);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount != null ? amount.abs() : BigDecimal.ZERO;
    }

    private String paymentStatus(int year, int month, int day) {
        return LocalDate.now().isAfter(LocalDate.of(year, month, day)) ? "past_due" : "upcoming";
    }

    private String deadlineStatus(int year, int month, int day) {
        return LocalDate.now().isAfter(LocalDate.of(year, month, day)) ? "overdue" : "upcoming";
    }

    private String getStableAccountId(Account account) {
        if (account.getAccountId() != null && !account.getAccountId().isBlank()) {
            return account.getAccountId();
        }

        if (account.getPlaidAccountId() != null && !account.getPlaidAccountId().isBlank()) {
            return account.getPlaidAccountId();
        }

        return account.getId() != null ? account.getId().toString() : "";
    }

    private String toId(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }

        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        return List.of("USD", "CAD").contains(normalized) ? normalized : DEFAULT_CURRENCY;
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private String clean(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private String fallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
