package com.Accounting.app.investments;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.Accounting.app.investments.dto.InvestmentHoldingsResponse;
import com.Accounting.app.investments.dto.InvestmentTransactionDto;
import com.Accounting.app.investments.dto.InvestmentTransactionsResponse;
import com.Accounting.app.exceptions.PlaidExceptions;
import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.auth.User;
import com.Accounting.app.plaid.EncryptionService;
import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.plaid.PlaidItemRepo;
import com.Accounting.app.auth.UserRepo;
import com.plaid.client.ApiClient;
import com.plaid.client.model.Holding;
import com.plaid.client.model.InvestmentAccount;
import com.plaid.client.model.InvestmentTransaction;
import com.plaid.client.model.InvestmentTransactionSubtype;
import com.plaid.client.model.InvestmentsHoldingsGetRequest;
import com.plaid.client.model.InvestmentsHoldingsGetResponse;
import com.plaid.client.model.InvestmentsTransactionsGetRequest;
import com.plaid.client.model.InvestmentsTransactionsGetRequestOptions;
import com.plaid.client.model.InvestmentsTransactionsGetResponse;
import com.plaid.client.model.Security;
import com.plaid.client.request.PlaidApi;

import retrofit2.Response;

@Service
public class PlaidInvestmentService {
    private static final int PAGE_SIZE = 500;
    private static final int DEFAULT_HISTORY_YEARS = 2;
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final Set<InvestmentTransactionType> STORED_TRANSACTION_TYPES = EnumSet.of(
            InvestmentTransactionType.BUY,
            InvestmentTransactionType.SELL,
            InvestmentTransactionType.DIVIDEND,
            InvestmentTransactionType.REINVESTMENT,
            InvestmentTransactionType.FEE);

    private final UserRepo userRepo;
    private final PlaidItemRepo plaidItemRepo;
    private final InvestmentTransactionRepo investmentTransactionRepo;
    private final InvestmentSecurityRepo investmentSecurityRepo;
    private final InvestmentHoldingRepo investmentHoldingRepo;
    private final InvestmentPortfolioSnapshotRepo investmentPortfolioSnapshotRepo;
    private final EncryptionService encryptionService;
    private final PlaidApi plaidClient;

    public PlaidInvestmentService(
            UserRepo userRepo,
            PlaidItemRepo plaidItemRepo,
            InvestmentTransactionRepo investmentTransactionRepo,
            InvestmentSecurityRepo investmentSecurityRepo,
            InvestmentHoldingRepo investmentHoldingRepo,
            InvestmentPortfolioSnapshotRepo investmentPortfolioSnapshotRepo,
            EncryptionService encryptionService,
            @Value("${app.plaid.client-id:}") String plaidClientId,
            @Value("${app.plaid.secret:}") String plaidSecret,
            @Value("${app.plaid.environment:production}") String plaidEnvironment) {
        this.userRepo = userRepo;
        this.plaidItemRepo = plaidItemRepo;
        this.investmentTransactionRepo = investmentTransactionRepo;
        this.investmentSecurityRepo = investmentSecurityRepo;
        this.investmentHoldingRepo = investmentHoldingRepo;
        this.investmentPortfolioSnapshotRepo = investmentPortfolioSnapshotRepo;
        this.encryptionService = encryptionService;

        Map<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", requiredPlaidConfig("PLAID_CLIENT_ID", plaidClientId));
        apiKeys.put("secret", requiredPlaidConfig("PLAID_SECRET", plaidSecret));

        ApiClient apiClient = new ApiClient(apiKeys);
        apiClient.setPlaidAdapter(plaidAdapter(plaidEnvironment));
        this.plaidClient = apiClient.createService(PlaidApi.class);
    }

    public InvestmentTransactionsResponse getInvestmentTransactions(String email) throws IOException {
        return syncInvestmentTransactionsForPlaidItem(getPlaidItem(email));
    }

    public List<InvestmentTransactionDto> syncInvestmentTransactionLogs(String email) throws IOException {
        return syncInvestmentTransactionsForPlaidItem(getPlaidItem(email)).getInvestmentTransactions();
    }

    public InvestmentTransactionsResponse syncInvestmentTransactionsForPlaidItem(PlaidItem plaidItem)
            throws IOException {
        markInvestmentSyncAttempt(plaidItem);
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusYears(DEFAULT_HISTORY_YEARS);
            String accessToken = encryptionService.decryptIfEncrypted(plaidItem.getAccessToken());
            List<InvestmentTransaction> allTransactions = new ArrayList<>();
            InvestmentsTransactionsGetResponse firstResponse = fetchInvestmentTransactions(
                    accessToken,
                    startDate,
                    endDate,
                    0);

            if (firstResponse.getInvestmentTransactions() != null) {
                allTransactions.addAll(firstResponse.getInvestmentTransactions());
            }

            int total = firstResponse.getTotalInvestmentTransactions() != null
                    ? firstResponse.getTotalInvestmentTransactions()
                    : allTransactions.size();
            int offset = PAGE_SIZE;

            while (offset < total) {
                InvestmentsTransactionsGetResponse page = fetchInvestmentTransactions(
                        accessToken,
                        startDate,
                        endDate,
                        offset);

                if (page.getInvestmentTransactions() != null) {
                    allTransactions.addAll(page.getInvestmentTransactions());
                }

                offset += PAGE_SIZE;
            }

            List<InvestmentAccount> accounts = firstResponse.getAccounts() != null
                    ? firstResponse.getAccounts()
                    : new ArrayList<>();
            List<Security> securities = firstResponse.getSecurities() != null
                    ? firstResponse.getSecurities()
                    : new ArrayList<>();
            List<InvestmentTransaction> investmentTransactions = allTransactions.stream()
                    .filter(this::isStoredInvestmentTransaction)
                    .toList();

            Map<String, InvestmentAccount> accountById = accounts.stream()
                    .filter(account -> account.getAccountId() != null)
                    .collect(Collectors.toMap(InvestmentAccount::getAccountId, Function.identity(), (left, right) -> left));
            Map<String, Security> securityById = securities.stream()
                    .filter(security -> security.getSecurityId() != null)
                    .collect(Collectors.toMap(Security::getSecurityId, Function.identity(), (left, right) -> left));

            upsertSecurities(securities);
            upsertInvestmentTransactions(plaidItem, allTransactions, accountById, securityById);

            List<InvestmentTransactionDto> mappedTransactions = investmentTransactions.stream()
                    .map(transaction -> toDto(transaction, accountById, securityById))
                    .toList();

            markInvestmentSyncSuccess(plaidItem);
            return new InvestmentTransactionsResponse(
                    accounts,
                    securities,
                    mappedTransactions,
                    mappedTransactions.size(),
                    firstResponse.getRequestId());
        } catch (IOException | RuntimeException ex) {
            markInvestmentSyncFailure(plaidItem, ex);
            throw ex;
        }
    }

    public InvestmentHoldingsResponse getInvestmentHoldings(String email) throws IOException {
        return syncInvestmentHoldingsForPlaidItem(getPlaidItem(email));
    }

    public InvestmentHoldingsResponse syncInvestmentHoldingsForPlaidItem(PlaidItem plaidItem) throws IOException {
        markInvestmentSyncAttempt(plaidItem);
        try {
            InvestmentsHoldingsGetRequest request = new InvestmentsHoldingsGetRequest()
                    .accessToken(encryptionService.decryptIfEncrypted(plaidItem.getAccessToken()));

            Response<InvestmentsHoldingsGetResponse> response = plaidClient.investmentsHoldingsGet(request).execute();

            if (!response.isSuccessful() || response.body() == null) {
                throw new PlaidExceptions("Failed to fetch Plaid investment holdings");
            }

            InvestmentsHoldingsGetResponse body = response.body();
            List<InvestmentAccount> accounts = body.getAccounts() != null ? body.getAccounts() : new ArrayList<>();
            List<Holding> holdings = body.getHoldings() != null ? body.getHoldings() : new ArrayList<>();
            List<Security> securities = body.getSecurities() != null ? body.getSecurities() : new ArrayList<>();

            Map<String, InvestmentAccount> accountById = accounts.stream()
                    .filter(account -> account.getAccountId() != null)
                    .collect(Collectors.toMap(InvestmentAccount::getAccountId, Function.identity(), (left, right) -> left));
            Map<String, Security> securityById = securities.stream()
                    .filter(security -> security.getSecurityId() != null)
                    .collect(Collectors.toMap(Security::getSecurityId, Function.identity(), (left, right) -> left));

            upsertSecurities(securities);
            upsertHoldings(plaidItem, holdings, accountById, securityById);
            savePortfolioSnapshots(plaidItem, holdings, accountById);

            markInvestmentSyncSuccess(plaidItem);
            return new InvestmentHoldingsResponse(
                    accounts,
                    holdings,
                    securities,
                    body.getRequestId());
        } catch (IOException | RuntimeException ex) {
            markInvestmentSyncFailure(plaidItem, ex);
            throw ex;
        }
    }

    private void markInvestmentSyncAttempt(PlaidItem plaidItem) {
        plaidItem.setLastInvestmentSyncAttemptAt(LocalDateTime.now());
        plaidItemRepo.save(plaidItem);
    }

    private void markInvestmentSyncSuccess(PlaidItem plaidItem) {
        plaidItem.setLastInvestmentSyncSuccessAt(LocalDateTime.now());
        plaidItem.setLastInvestmentSyncError(null);
        plaidItemRepo.save(plaidItem);
    }

    private void markInvestmentSyncFailure(PlaidItem plaidItem, Exception exception) {
        plaidItem.setLastInvestmentSyncFailureAt(LocalDateTime.now());
        plaidItem.setLastInvestmentSyncError(truncateError(exception));
        plaidItemRepo.save(plaidItem);
    }

    private String truncateError(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private InvestmentsTransactionsGetResponse fetchInvestmentTransactions(
            String accessToken,
            LocalDate startDate,
            LocalDate endDate,
            int offset) throws IOException {
        InvestmentsTransactionsGetRequest request = new InvestmentsTransactionsGetRequest()
                .accessToken(accessToken)
                .startDate(startDate)
                .endDate(endDate)
                .options(new InvestmentsTransactionsGetRequestOptions()
                        .count(PAGE_SIZE)
                        .offset(offset));

        Response<InvestmentsTransactionsGetResponse> response = plaidClient.investmentsTransactionsGet(request)
                .execute();

        if (!response.isSuccessful() || response.body() == null) {
            throw new PlaidExceptions("Failed to fetch Plaid investment transactions");
        }

        return response.body();
    }

    private void upsertSecurities(List<Security> securities) {
        for (Security plaidSecurity : securities) {
            if (plaidSecurity.getSecurityId() == null) {
                continue;
            }

            InvestmentSecurity security = investmentSecurityRepo.findBySecurityId(plaidSecurity.getSecurityId())
                    .orElseGet(InvestmentSecurity::new);

            security.setSecurityId(plaidSecurity.getSecurityId());
            security.setName(plaidSecurity.getName());
            security.setTicker(plaidSecurity.getTickerSymbol());
            security.setType(plaidSecurity.getType());
            security.setSubtype(plaidSecurity.getSubtype());
            security.setClosePrice(toBigDecimal(plaidSecurity.getClosePrice()));
            security.setClosePriceAsOf(plaidSecurity.getClosePriceAsOf());
            security.setCurrency(currency(plaidSecurity.getIsoCurrencyCode()));
            security.setUpdatedAt(LocalDateTime.now());

            investmentSecurityRepo.save(security);
        }
    }

    private void upsertInvestmentTransactions(
            PlaidItem plaidItem,
            List<InvestmentTransaction> plaidTransactions,
            Map<String, InvestmentAccount> accountById,
            Map<String, Security> securityById) {
        for (InvestmentTransaction plaidTransaction : plaidTransactions) {
            if (plaidTransaction.getInvestmentTransactionId() == null) {
                continue;
            }

            InvestmentTransactionType transactionType = normalizeType(
                    plaidTransaction.getType(),
                    plaidTransaction.getSubtype());
            if (!isStoredInvestmentTransaction(transactionType)) {
                investmentTransactionRepo
                        .findByPlaidInvestmentTransactionId(plaidTransaction.getInvestmentTransactionId())
                        .ifPresent(investmentTransactionRepo::delete);
                continue;
            }

            InvestmentAccount account = accountById.get(plaidTransaction.getAccountId());
            Security security = securityById.get(plaidTransaction.getSecurityId());
            com.Accounting.app.investments.InvestmentTransaction transaction = investmentTransactionRepo
                    .findByPlaidInvestmentTransactionId(plaidTransaction.getInvestmentTransactionId())
                    .orElseGet(com.Accounting.app.investments.InvestmentTransaction::new);

            transaction.setPlaidInvestmentTransactionId(plaidTransaction.getInvestmentTransactionId());
            transaction.setPlaidItem(plaidItem);
            transaction.setAccountId(plaidTransaction.getAccountId());
            transaction.setAccountName(account != null ? account.getName() : plaidTransaction.getAccountId());
            transaction.setSecurityId(plaidTransaction.getSecurityId());
            transaction.setSecurityName(security != null ? security.getName() : plaidTransaction.getName());
            transaction.setTicker(security != null ? security.getTickerSymbol() : null);
            transaction.setType(transactionType);
            transaction.setAmount(toBigDecimal(plaidTransaction.getAmount()));
            transaction.setQuantity(toBigDecimal(plaidTransaction.getQuantity()));
            transaction.setPrice(toBigDecimal(plaidTransaction.getPrice()));
            transaction.setDate(plaidTransaction.getDate());
            transaction.setCurrency(currency(plaidTransaction.getIsoCurrencyCode()));
            transaction.setSyncedAt(LocalDateTime.now());

            investmentTransactionRepo.save(transaction);
        }
    }

    private void upsertHoldings(
            PlaidItem plaidItem,
            List<Holding> plaidHoldings,
            Map<String, InvestmentAccount> accountById,
            Map<String, Security> securityById) {
        for (Holding plaidHolding : plaidHoldings) {
            if (plaidHolding.getAccountId() == null || plaidHolding.getSecurityId() == null) {
                continue;
            }

            InvestmentAccount account = accountById.get(plaidHolding.getAccountId());
            Security security = securityById.get(plaidHolding.getSecurityId());
            InvestmentHolding holding = investmentHoldingRepo
                    .findByPlaidItemAndAccountIdAndSecurityId(
                            plaidItem,
                            plaidHolding.getAccountId(),
                            plaidHolding.getSecurityId())
                    .orElseGet(InvestmentHolding::new);

            holding.setPlaidItem(plaidItem);
            holding.setAccountId(plaidHolding.getAccountId());
            holding.setAccountName(account != null ? account.getName() : plaidHolding.getAccountId());
            holding.setSecurityId(plaidHolding.getSecurityId());
            holding.setSecurityName(security != null ? security.getName() : plaidHolding.getSecurityId());
            holding.setTicker(security != null ? security.getTickerSymbol() : null);
            holding.setQuantity(toBigDecimal(plaidHolding.getQuantity()));
            holding.setInstitutionPrice(toBigDecimal(plaidHolding.getInstitutionPrice()));
            holding.setInstitutionValue(toBigDecimal(plaidHolding.getInstitutionValue()));
            holding.setCurrency(currency(plaidHolding.getIsoCurrencyCode()));
            holding.setSyncedAt(LocalDateTime.now());

            investmentHoldingRepo.save(holding);
        }
    }

    private void savePortfolioSnapshots(
            PlaidItem plaidItem,
            List<Holding> plaidHoldings,
            Map<String, InvestmentAccount> accountById) {
        LocalDateTime snapshotAt = LocalDateTime.now();
        String email = plaidItem.getUser() != null ? plaidItem.getUser().getEmail() : null;
        Map<String, BigDecimal> totalsByAccount = new HashMap<>();
        Map<String, String> namesByAccount = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        String currency = DEFAULT_CURRENCY;

        for (Holding holding : plaidHoldings) {
            if (holding.getAccountId() == null) {
                continue;
            }
            BigDecimal value = toBigDecimal(holding.getInstitutionValue());
            total = total.add(value);
            totalsByAccount.merge(holding.getAccountId(), value, BigDecimal::add);
            InvestmentAccount account = accountById.get(holding.getAccountId());
            namesByAccount.putIfAbsent(holding.getAccountId(), account != null ? account.getName() : holding.getAccountId());
            if (holding.getIsoCurrencyCode() != null && !holding.getIsoCurrencyCode().isBlank()) {
                currency = holding.getIsoCurrencyCode();
            }
        }

        savePortfolioSnapshot(plaidItem, email, "all", "All investment accounts", total, currency, snapshotAt);
        for (Map.Entry<String, BigDecimal> entry : totalsByAccount.entrySet()) {
            savePortfolioSnapshot(plaidItem, email, entry.getKey(), namesByAccount.get(entry.getKey()), entry.getValue(), currency, snapshotAt);
        }
    }

    private void savePortfolioSnapshot(
            PlaidItem plaidItem,
            String email,
            String accountId,
            String accountName,
            BigDecimal totalValue,
            String currency,
            LocalDateTime snapshotAt) {
        InvestmentPortfolioSnapshot snapshot = new InvestmentPortfolioSnapshot();
        snapshot.setPlaidItem(plaidItem);
        snapshot.setEmail(email);
        snapshot.setAccountId(accountId);
        snapshot.setAccountName(accountName);
        snapshot.setTotalValue(totalValue.setScale(2, java.math.RoundingMode.HALF_UP));
        snapshot.setCurrency(currency(currency));
        snapshot.setSnapshotAt(snapshotAt);
        investmentPortfolioSnapshotRepo.save(snapshot);
    }
    private InvestmentTransactionDto toDto(
            InvestmentTransaction transaction,
            Map<String, InvestmentAccount> accountById,
            Map<String, Security> securityById) {
        InvestmentAccount account = accountById.get(transaction.getAccountId());
        Security security = securityById.get(transaction.getSecurityId());

        return new InvestmentTransactionDto(
                transaction.getInvestmentTransactionId(),
                account != null ? account.getName() : transaction.getAccountId(),
                security != null ? security.getName() : transaction.getName(),
                security != null ? security.getTickerSymbol() : null,
                normalizeType(transaction.getType(), transaction.getSubtype()).name(),
                toBigDecimal(transaction.getAmount()),
                toBigDecimal(transaction.getQuantity()),
                toBigDecimal(transaction.getPrice()),
                transaction.getDate(),
                currency(transaction.getIsoCurrencyCode()));
    }

    private InvestmentTransactionType normalizeType(
            com.plaid.client.model.InvestmentTransactionType type,
            InvestmentTransactionSubtype subtype) {
        String subtypeValue = subtype != null ? subtype.getValue().toUpperCase() : "";

        if (subtypeValue.contains("REINVESTMENT")) {
            return InvestmentTransactionType.REINVESTMENT;
        }

        if (subtypeValue.contains("DIVIDEND")) {
            return InvestmentTransactionType.DIVIDEND;
        }

        if (subtypeValue.contains("FEE") || subtypeValue.contains("TAX")) {
            return InvestmentTransactionType.FEE;
        }

        if (type == com.plaid.client.model.InvestmentTransactionType.BUY) {
            return InvestmentTransactionType.BUY;
        }

        if (type == com.plaid.client.model.InvestmentTransactionType.SELL) {
            return InvestmentTransactionType.SELL;
        }

        if (type == com.plaid.client.model.InvestmentTransactionType.FEE) {
            return InvestmentTransactionType.FEE;
        }

        if (type == com.plaid.client.model.InvestmentTransactionType.CASH) {
            return InvestmentTransactionType.CASH;
        }

        if (type == com.plaid.client.model.InvestmentTransactionType.TRANSFER) {
            return InvestmentTransactionType.TRANSFER;
        }

        return InvestmentTransactionType.OTHER;
    }

    private boolean isStoredInvestmentTransaction(InvestmentTransaction transaction) {
        return isStoredInvestmentTransaction(normalizeType(transaction.getType(), transaction.getSubtype()));
    }

    private boolean isStoredInvestmentTransaction(InvestmentTransactionType type) {
        return STORED_TRANSACTION_TYPES.contains(type);
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : BigDecimal.ZERO;
    }

    private String currency(String value) {
        return value != null && !value.isBlank() ? value : DEFAULT_CURRENCY;
    }

    private PlaidItem getPlaidItem(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return plaidItemRepo.findByUserId(user.getUserId())
                .orElseThrow(() -> new PlaidExceptions("Plaid item not found"));
    }

    private String requiredPlaidConfig(String name, String value) {
        if (!hasText(value)) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private String plaidAdapter(String environment) {
        String normalized = environment == null ? "production" : environment.toLowerCase(Locale.ROOT).trim();
        return switch (normalized) {
            case "sandbox" -> ApiClient.Sandbox;
            case "production", "prod" -> ApiClient.Production;
            default -> throw new IllegalStateException("Unsupported PLAID_ENVIRONMENT: " + environment);
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}


