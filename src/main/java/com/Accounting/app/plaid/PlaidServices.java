package com.Accounting.app.plaid;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.Accounting.app.exceptions.PlaidExceptions;
import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.accounts.Account;
import com.Accounting.app.accounts.AccountBalanceSnapshot;
import com.Accounting.app.accounts.AccountBalanceSnapshotRepo;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.transactions.TransactionClassificationService;
import com.Accounting.app.transactions.TransactionType;
import com.Accounting.app.transactions.TransferMatchingService;
import com.Accounting.app.auth.User;
import com.Accounting.app.investments.PlaidInvestmentService;
import com.Accounting.app.plaid.dto.InstitutionMetadataDto;
import com.Accounting.app.plaid.dto.PlaidLinkTokenCreateRequest;
import com.Accounting.app.plaid.dto.PlaidSyncAllResponse;
import com.Accounting.app.accounts.AccountRepo;
import com.Accounting.app.transactions.TransactionsRepo;
import com.Accounting.app.auth.UserRepo;
import com.plaid.client.ApiClient;
import com.plaid.client.model.CountryCode;
import com.plaid.client.model.LinkTokenCreateRequest;
import com.plaid.client.model.LinkTokenCreateRequestUser;
import com.plaid.client.model.Products;
import com.plaid.client.model.RemovedTransaction;
import com.plaid.client.model.TransactionsSyncRequest;
import com.plaid.client.model.TransactionsSyncResponse;
import com.plaid.client.request.PlaidApi;
import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountsGetRequest;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.model.Institution;
import com.plaid.client.model.InstitutionsGetByIdRequest;
import com.plaid.client.model.InstitutionsGetByIdRequestOptions;
import com.plaid.client.model.InstitutionsGetByIdResponse;
import com.plaid.client.model.InstitutionsSearchRequest;
import com.plaid.client.model.InstitutionsSearchRequestOptions;
import com.plaid.client.model.InstitutionsSearchResponse;
import com.plaid.client.model.ItemGetRequest;
import com.plaid.client.model.ItemGetResponse;
import retrofit2.Response;
import com.plaid.client.model.ItemPublicTokenExchangeRequest;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;

@Service
public class PlaidServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private final PlaidItemRepo plaidItemRepo;
    private final UserRepo userRepo;
    private final PlaidApi plaidClient;
    private final TransactionsRepo transactionsRepo;
    private final AccountRepo accountRepo;
    private final AccountBalanceSnapshotRepo accountBalanceSnapshotRepo;
    private final PlaidInvestmentService plaidInvestmentService;
    private final EncryptionService encryptionService;
    private final TransactionClassificationService transactionClassificationService;
    private final TransferMatchingService transferMatchingService;

    public PlaidServices(PlaidItemRepo plaidItemRepo, UserRepo userRepo, TransactionsRepo transactionsRepo,
            AccountRepo accountRepo, AccountBalanceSnapshotRepo accountBalanceSnapshotRepo,
            PlaidInvestmentService plaidInvestmentService,
            EncryptionService encryptionService,
            TransactionClassificationService transactionClassificationService,
            TransferMatchingService transferMatchingService,
            @Value("${app.plaid.client-id:}") String plaidClientId,
            @Value("${app.plaid.secret:}") String plaidSecret,
            @Value("${app.plaid.environment:production}") String plaidEnvironment) {
        this.plaidItemRepo = plaidItemRepo;
        this.userRepo = userRepo;
        this.transactionsRepo = transactionsRepo;
        this.accountRepo = accountRepo;
        this.accountBalanceSnapshotRepo = accountBalanceSnapshotRepo;
        this.plaidInvestmentService = plaidInvestmentService;
        this.encryptionService = encryptionService;
        this.transactionClassificationService = transactionClassificationService;
        this.transferMatchingService = transferMatchingService;

        Map<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", requiredPlaidConfig("PLAID_CLIENT_ID", plaidClientId));
        apiKeys.put("secret", requiredPlaidConfig("PLAID_SECRET", plaidSecret));

        ApiClient apiClient = new ApiClient(apiKeys);
        apiClient.setPlaidAdapter(plaidAdapter(plaidEnvironment));

        this.plaidClient = apiClient.createService(PlaidApi.class);
    }

    public List<AccountBase> getBankAccounts(String email) {
        User user = userRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        PlaidItem existing = plaidItemRepo.findByUserId(user.getUserId())
                .orElseThrow(() -> new PlaidExceptions("Plaid item not found."));

        AccountsGetRequest request = new AccountsGetRequest()
                .accessToken(encryptionService.decryptIfEncrypted(existing.getAccessToken()));

        try {
            Response<AccountsGetResponse> response = plaidClient.accountsGet(request).execute();
            return response.body().getAccounts();

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }

        return null;
    }

    public String createLinkToken(String email) throws IOException {
        return createLinkToken(email, null);
    }

    public String createLinkToken(String email, PlaidLinkTokenCreateRequest linkRequest) throws IOException {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        LinkTokenCreateRequestUser plaidUser = new LinkTokenCreateRequestUser()
                .clientUserId(user.getUserId().toString());

        List<String> capabilities = mergeRequestedCapabilities(
                linkRequest != null ? linkRequest.getRequestedCapabilities() : null,
                linkRequest != null ? linkRequest.getRequestedProducts() : null,
                linkRequest != null ? linkRequest.getRequestedDataScopes() : null);

        LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                .user(plaidUser)
                .clientName("Accounting App")
                .products(requestedPlaidProducts(capabilities))
                .countryCodes(Arrays.asList(CountryCode.CA))
                .language("en");

        retrofit2.Response<com.plaid.client.model.LinkTokenCreateResponse> response = plaidClient
                .linkTokenCreate(request)
                .execute();

        if (!response.isSuccessful() || response.body() == null) {
            throw new PlaidExceptions("Failed to create Plaid link token");
        }

        return response.body().getLinkToken();
    }

    public String exchangePublicToken(String email, String publicToken, String institutionId, String institutionName)
            throws IOException {
        return exchangePublicToken(email, publicToken, institutionId, institutionName, null, null, null);
    }

    public String exchangePublicToken(
            String email,
            String publicToken,
            String institutionId,
            String institutionName,
            List<String> requestedCapabilities,
            List<String> requestedProducts,
            List<String> requestedDataScopes)
            throws IOException {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        ItemPublicTokenExchangeRequest plaidRequest = new ItemPublicTokenExchangeRequest()
                .publicToken(publicToken);

        Response<ItemPublicTokenExchangeResponse> response = plaidClient.itemPublicTokenExchange(plaidRequest)
                .execute();

        if (!response.isSuccessful() || response.body() == null) {
            throw new PlaidExceptions("Failed to exchange public token");
        }

        String accessToken = response.body().getAccessToken();

        PlaidItem plaidItem = new PlaidItem();
        plaidItem.setUser(user);

        plaidItem.setAccessToken(encryptionService.encrypt(accessToken));
        plaidItem.setPlaidItemId(response.body().getItemId());
        plaidItem.setInstitutionId(institutionId);
        plaidItem.setInstitutionName(institutionName);
        List<String> capabilities = mergeRequestedCapabilities(
                requestedCapabilities,
                requestedProducts,
                requestedDataScopes);
        plaidItem.setRequestedCapabilities(capabilities);
        plaidItem.setRequestedProducts(filterProductCapabilities(capabilities));
        plaidItem.setRequestedDataScopes(normalizeList(requestedDataScopes));
        populateItemInstitution(plaidItem, accessToken);
        populateInstitutionMetadata(plaidItem);

        PlaidItem savedPlaidItem = plaidItemRepo.save(plaidItem);
        refreshAccountsForPlaidItem(user, savedPlaidItem);

        return "Bank account connected successfully";

    }

    public List<AccountBase> refreshAccounts(String email) {
        User user = userRepo.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found"));
        PlaidItem plaidItem = plaidItemRepo.findByUserId(user.getUserId())
                .orElseThrow(() -> new PlaidExceptions("Plaid item not found."));

        return refreshAccountsForPlaidItem(user, plaidItem);
    }

    public InstitutionMetadataDto getInstitutionById(String institutionId) throws IOException {
        if (!hasText(institutionId)) {
            throw new PlaidExceptions("Institution id is required");
        }

        InstitutionsGetByIdRequest request = new InstitutionsGetByIdRequest()
                .institutionId(institutionId)
                .countryCodes(Arrays.asList(CountryCode.CA))
                .options(new InstitutionsGetByIdRequestOptions().includeOptionalMetadata(true));

        Response<InstitutionsGetByIdResponse> response = plaidClient.institutionsGetById(request).execute();
        if (!response.isSuccessful() || response.body() == null || response.body().getInstitution() == null) {
            throw new PlaidExceptions("Failed to fetch Plaid institution metadata");
        }

        return toInstitutionMetadataDto(response.body().getInstitution());
    }

    public List<InstitutionMetadataDto> searchInstitutions(String query) throws IOException {
        if (!hasText(query)) {
            throw new PlaidExceptions("Institution search query is required");
        }

        InstitutionsSearchRequest request = new InstitutionsSearchRequest()
                .query(query)
                .countryCodes(Arrays.asList(CountryCode.CA))
                .options(new InstitutionsSearchRequestOptions().includeOptionalMetadata(true));

        Response<InstitutionsSearchResponse> response = plaidClient.institutionsSearch(request).execute();
        if (!response.isSuccessful() || response.body() == null || response.body().getInstitutions() == null) {
            throw new PlaidExceptions("Failed to search Plaid institutions");
        }

        return response.body().getInstitutions().stream()
                .map(this::toInstitutionMetadataDto)
                .toList();
    }

    public void handlePlaidWebhook(Map<String, Object> payload) throws IOException {
        String plaidItemId = getString(payload, "item_id");
        String webhookType = getString(payload, "webhook_type");

        if (plaidItemId == null || plaidItemId.isBlank()) {
            return;
        }

        PlaidItem plaidItem = plaidItemRepo.findByPlaidItemId(plaidItemId)
                .orElseThrow(() -> new PlaidExceptions("Plaid item not found for webhook"));

        if ("TRANSACTIONS".equalsIgnoreCase(webhookType)) {
            if (shouldSyncTransactions(plaidItem)) {
                syncTransactionsForPlaidItem(plaidItem);
            }
            return;
        }

        if ("INVESTMENTS".equalsIgnoreCase(webhookType)) {
            if (shouldSyncInvestments(plaidItem)) {
                plaidInvestmentService.syncInvestmentTransactionsForPlaidItem(plaidItem);
                plaidInvestmentService.syncInvestmentHoldingsForPlaidItem(plaidItem);
            }
            return;
        }

        if ("ITEM".equalsIgnoreCase(webhookType)) {
            refreshAccountsForPlaidItem(plaidItem.getUser(), plaidItem);
        }
    }

    public PlaidSyncAllResponse syncAll(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        List<PlaidItem> plaidItems = plaidItemRepo.findAllByUser_Email(email);
        if (plaidItems.isEmpty()) {
            throw new PlaidExceptions("Plaid item not found");
        }

        PlaidSyncAllResponse result = new PlaidSyncAllResponse();
        for (PlaidItem plaidItem : plaidItems) {
            String institution = fallback(plaidItem.getInstitutionName(), "Plaid item");

            try {
                refreshAccountsForPlaidItem(user, plaidItem);
                result.setAccountsSynced(true);
            } catch (RuntimeException ex) {
                result.error(institution + " accounts: " + safeMessage(ex));
            }

            if (shouldSyncTransactions(plaidItem)) {
                try {
                    syncTransactionsForPlaidItem(plaidItem);
                    result.setTransactionsSynced(true);
                } catch (IOException | RuntimeException ex) {
                    result.error(institution + " transactions: " + safeMessage(ex));
                }
            } else {
                result.skip(institution + ": transactions were not requested or are not enabled");
            }

            if (shouldSyncInvestments(plaidItem)) {
                try {
                    plaidInvestmentService.syncInvestmentTransactionsForPlaidItem(plaidItem);
                    result.setInvestmentTransactionsSynced(true);
                } catch (IOException | RuntimeException ex) {
                    result.error(institution + " investment transactions: " + safeMessage(ex));
                }

                try {
                    plaidInvestmentService.syncInvestmentHoldingsForPlaidItem(plaidItem);
                    result.setInvestmentHoldingsSynced(true);
                } catch (IOException | RuntimeException ex) {
                    result.error(institution + " investment holdings: " + safeMessage(ex));
                }
            } else {
                result.skip(institution + ": investments were not requested or are not enabled");
            }

            if (requestedCapability(plaidItem, "assets")) {
                result.skip(institution + ": asset sync is not implemented yet");
            }
            if (requestedCapability(plaidItem, "liabilities")) {
                result.skip(institution + ": liability sync is not implemented yet");
            }
        }

        return result;
    }

    private List<String> resolveMetadata(com.plaid.client.model.Transaction plaidTransaction) {
        List<String> metadata = new ArrayList<>();

        if (plaidTransaction.getIsoCurrencyCode() != null) {
            metadata.add("currency=" + plaidTransaction.getIsoCurrencyCode());
        }

        if (plaidTransaction.getPaymentChannel() != null) {
            metadata.add("paymentChannel=" + plaidTransaction.getPaymentChannel().getValue());
        }

        if (plaidTransaction.getWebsite() != null) {
            metadata.add("website=" + plaidTransaction.getWebsite());
        }

        if (plaidTransaction.getLogoUrl() != null) {
            metadata.add("logoUrl=" + plaidTransaction.getLogoUrl());
        }

        if (plaidTransaction.getPersonalFinanceCategory() != null &&
                plaidTransaction.getPersonalFinanceCategory().getConfidenceLevel() != null) {
            metadata.add("categoryConfidence=" +
                    plaidTransaction.getPersonalFinanceCategory().getConfidenceLevel());
        }

        return metadata;
    }

    private void applyPlaidTransaction(
            Transaction transaction,
            com.plaid.client.model.Transaction plaidTransaction,
            Account account) {
        transaction.setPlaidTransactionId(plaidTransaction.getTransactionId());
        transaction.setAmount(plaidTransaction.getAmount() != null
                ? BigDecimal.valueOf(plaidTransaction.getAmount())
                : BigDecimal.ZERO);
        transaction.setRawMerchantName(plaidTransaction.getMerchantName());
        transaction.setMerchantName(transactionClassificationService.resolveDisplayMerchantName(plaidTransaction, account));
        transaction.setDescription(plaidTransaction.getName());
        transaction.setPlaidName(plaidTransaction.getName());
        transaction.setWebsite(plaidTransaction.getWebsite());
        transaction.setLogoUrl(plaidTransaction.getLogoUrl());
        transaction.setPlaidAccountId(plaidTransaction.getAccountId());
        transaction.setIsoCurrencyCode(plaidTransaction.getIsoCurrencyCode());
        transaction.setPaymentChannel(plaidTransaction.getPaymentChannel() != null
                ? plaidTransaction.getPaymentChannel().getValue()
                : null);
        transaction.setPending(Boolean.TRUE.equals(plaidTransaction.getPending()));
        transaction.setPlaidCategoryPrimary(transactionClassificationService.resolvePlaidCategoryPrimary(plaidTransaction));
        transaction.setPlaidCategoryDetailed(transactionClassificationService.resolvePlaidCategoryDetailed(plaidTransaction));
        if (plaidTransaction.getPersonalFinanceCategory() != null
                && plaidTransaction.getPersonalFinanceCategory().getConfidenceLevel() != null) {
            transaction.setPlaidCategoryConfidence(
                    plaidTransaction.getPersonalFinanceCategory().getConfidenceLevel().toString());
        }
        transaction.setDisplayCategory(transactionClassificationService.resolveDisplayCategory(plaidTransaction, account));
        transaction.setTimestamp(plaidTransaction.getDate() != null
                ? plaidTransaction.getDate().atStartOfDay()
                : LocalDateTime.now());
        TransactionType type = transactionClassificationService.resolveTransactionType(plaidTransaction, account);
        transaction.setTransactionType(type);
        transaction.setTransfer(type == TransactionType.TRANSFER);
        transaction.setIncludedInCashFlow(transactionClassificationService.shouldIncludeInCashFlow(type));
        if (type == TransactionType.TRANSFER) {
            transaction.setTaxRelevant(false);
            transaction.setNeedsReview(false);
            transaction.setReviewReason(null);
        }
        transaction.setMetadata(resolveMetadata(plaidTransaction));
        transaction.setAccount(account);
    }

    public List<com.plaid.client.model.Transaction> syncTransactions(String email) throws IOException {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        PlaidItem plaidItem = plaidItemRepo.findByUserId(user.getUserId())
                .orElseThrow(() -> new PlaidExceptions("Plaid item not found"));

        return syncTransactionsForPlaidItem(plaidItem);
    }

    private List<com.plaid.client.model.Transaction> syncTransactionsForPlaidItem(PlaidItem plaidItem)
            throws IOException {
        String cursor = plaidItem.getSyncCursor();

        List<com.plaid.client.model.Transaction> changedTransactions = new ArrayList<>();

        boolean hasMore = true;

        while (hasMore) {
            String accessToken = encryptionService.decryptIfEncrypted(plaidItem.getAccessToken());
            TransactionsSyncRequest request = new TransactionsSyncRequest()
                    .accessToken(accessToken)
                    .cursor(cursor);

            Response<TransactionsSyncResponse> response = plaidClient.transactionsSync(request).execute();

            if (!response.isSuccessful() || response.body() == null) {
                throw new PlaidExceptions("Failed to sync transactions");
            }

            TransactionsSyncResponse body = response.body();

            List<com.plaid.client.model.Transaction> added = body.getAdded() != null
                    ? body.getAdded()
                    : new ArrayList<>();
            List<com.plaid.client.model.Transaction> modified = body.getModified() != null
                    ? body.getModified()
                    : new ArrayList<>();
            List<RemovedTransaction> removed = body.getRemoved() != null
                    ? body.getRemoved()
                    : new ArrayList<>();

            upsertTransactions(plaidItem, added);
            upsertTransactions(plaidItem, modified);
            removeTransactions(plaidItem, removed);
            changedTransactions.addAll(added);
            changedTransactions.addAll(modified);

            cursor = body.getNextCursor();
            hasMore = Boolean.TRUE.equals(body.getHasMore());
        }

        plaidItem.setSyncCursor(cursor);
        plaidItemRepo.save(plaidItem);
        if (plaidItem.getUser() != null) {
            transferMatchingService.matchForUser(plaidItem.getUser().getUserId());
        }

        return changedTransactions;
    }

    private void upsertTransactions(PlaidItem plaidItem, List<com.plaid.client.model.Transaction> plaidTransactions) {
        for (com.plaid.client.model.Transaction plaidTransaction : plaidTransactions) {
            if (plaidTransaction.getTransactionId() == null || plaidTransaction.getAccountId() == null) {
                continue;
            }

            Account account = accountRepo.findByAccountIdAndPlaidItem(plaidTransaction.getAccountId(), plaidItem)
                    .orElseThrow(() -> new UserNotFoundException("Invalid credentials"));
            Transaction transaction = transactionsRepo
                    .findByPlaidTransactionIdAndAccount_PlaidItem(plaidTransaction.getTransactionId(), plaidItem)
                    .orElseGet(Transaction::new);

            applyPlaidTransaction(transaction, plaidTransaction, account);
            transactionsRepo.save(transaction);
        }
    }

    private void removeTransactions(PlaidItem plaidItem, List<RemovedTransaction> removedTransactions) {
        for (RemovedTransaction removedTransaction : removedTransactions) {
            if (removedTransaction.getTransactionId() == null) {
                continue;
            }

            transactionsRepo
                    .findByPlaidTransactionIdAndAccount_PlaidItem(removedTransaction.getTransactionId(), plaidItem)
                    .ifPresent(transactionsRepo::delete);
        }
    }

    private List<AccountBase> refreshAccountsForPlaidItem(User user, PlaidItem plaidItem) {
        markAccountSyncAttempt(plaidItem);
        try {
            String accessToken = encryptionService.decryptIfEncrypted(plaidItem.getAccessToken());
            populateItemInstitution(plaidItem, accessToken);
            populateInstitutionMetadata(plaidItem);
            plaidItemRepo.save(plaidItem);
            List<AccountBase> plaidAccounts = getBankAccountsForPlaidItem(plaidItem);
            LocalDateTime syncedAt = LocalDateTime.now();
            upsertAccounts(user, plaidItem, plaidAccounts, syncedAt);
            markAccountSyncSuccess(plaidItem, syncedAt);
            return plaidAccounts;
        } catch (RuntimeException ex) {
            markAccountSyncFailure(plaidItem, ex);
            throw ex;
        }
    }

    private List<AccountBase> getBankAccountsForPlaidItem(PlaidItem plaidItem) {
        AccountsGetRequest request = new AccountsGetRequest()
                .accessToken(encryptionService.decryptIfEncrypted(plaidItem.getAccessToken()));

        try {
            Response<AccountsGetResponse> response = plaidClient.accountsGet(request).execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getAccounts() == null) {
                throw new PlaidExceptions("Failed to fetch Plaid accounts");
            }

            return response.body().getAccounts();
        } catch (IOException ex) {
            throw new PlaidExceptions("Failed to fetch Plaid accounts");
        }
    }

    private void upsertAccounts(User user, PlaidItem plaidItem, List<AccountBase> plaidAccounts,
            LocalDateTime syncedAt) {
        for (AccountBase plaidAccount : plaidAccounts) {
            Account account = accountRepo.findByPlaidAccountIdAndPlaidItem(plaidAccount.getAccountId(), plaidItem)
                    .orElseGet(Account::new);

            BigDecimal currentBalance = plaidAccount.getBalances() != null
                    && plaidAccount.getBalances().getCurrent() != null
                            ? BigDecimal.valueOf(plaidAccount.getBalances().getCurrent())
                            : BigDecimal.ZERO;
            BigDecimal availableBalance = plaidAccount.getBalances() != null
                    && plaidAccount.getBalances().getAvailable() != null
                            ? BigDecimal.valueOf(plaidAccount.getBalances().getAvailable())
                            : currentBalance;

            account.setUser(user);
            account.setPlaidItem(plaidItem);
            account.setEmail(user.getEmail());
            account.setAccountId(plaidAccount.getAccountId());
            account.setPlaidAccountId(plaidAccount.getAccountId());
            account.setAccountName(plaidAccount.getName());
            account.setOfficialName(plaidAccount.getOfficialName());
            account.setType(plaidAccount.getType() != null ? plaidAccount.getType().getValue() : null);
            account.setSubtype(plaidAccount.getSubtype() != null ? plaidAccount.getSubtype().getValue() : null);
            account.setMask(formatMask(plaidAccount.getMask()));
            account.setPreviousBalance(account.getBalance());
            account.setPreviousAvailableBalance(account.getAvailableBalance());
            account.setBalance(currentBalance);
            account.setAvailableBalance(availableBalance);
            account.setDateAdded(account.getDateAdded() != null ? account.getDateAdded() : syncedAt);
            account.setLastSyncAttemptAt(syncedAt);
            account.setLastSyncSuccessAt(syncedAt);
            account.setLastSyncError(null);
            account.setUnofficialName(plaidAccount.getName());

            Account savedAccount = accountRepo.save(account);
            saveAccountSnapshot(savedAccount, plaidItem, currentBalance, availableBalance, syncedAt);
        }
    }

    private void markAccountSyncAttempt(PlaidItem plaidItem) {
        plaidItem.setLastAccountSyncAttemptAt(LocalDateTime.now());
        plaidItemRepo.save(plaidItem);
    }

    private void markAccountSyncSuccess(PlaidItem plaidItem, LocalDateTime syncedAt) {
        plaidItem.setLastAccountSyncSuccessAt(syncedAt);
        plaidItem.setLastAccountSyncError(null);
        plaidItemRepo.save(plaidItem);
    }

    private void markAccountSyncFailure(PlaidItem plaidItem, Exception exception) {
        plaidItem.setLastAccountSyncFailureAt(LocalDateTime.now());
        plaidItem.setLastAccountSyncError(truncateError(exception));
        plaidItemRepo.save(plaidItem);
    }

    private void saveAccountSnapshot(
            Account account,
            PlaidItem plaidItem,
            BigDecimal balance,
            BigDecimal availableBalance,
            LocalDateTime syncedAt) {
        AccountBalanceSnapshot snapshot = new AccountBalanceSnapshot();
        snapshot.setEmail(account.getEmail());
        snapshot.setAccountId(account.getAccountId());
        snapshot.setAccountName(account.getAccountName());
        snapshot.setAccountType(account.getType());
        snapshot.setAccountSubtype(account.getSubtype());
        snapshot.setBalance(balance);
        snapshot.setAvailableBalance(availableBalance);
        snapshot.setCurrency(DEFAULT_CURRENCY);
        snapshot.setSnapshotAt(syncedAt);
        snapshot.setPlaidItem(plaidItem);
        accountBalanceSnapshotRepo.save(snapshot);
    }

    private String truncateError(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private void populateItemInstitution(PlaidItem plaidItem, String accessToken) {
        try {
            ItemGetRequest request = new ItemGetRequest().accessToken(accessToken);
            Response<ItemGetResponse> response = plaidClient.itemGet(request).execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getItem() == null) {
                return;
            }

            var item = response.body().getItem();
            if (!hasText(plaidItem.getInstitutionId())) {
                plaidItem.setInstitutionId(item.getInstitutionId());
            }
            if (!hasText(plaidItem.getInstitutionName())) {
                plaidItem.setInstitutionName(item.getInstitutionName());
            }
            if (item.getProducts() != null) {
                plaidItem.setEnabledProducts(productValues(item.getProducts()));
            }
            if (item.getAvailableProducts() != null) {
                plaidItem.setAvailableProducts(productValues(item.getAvailableProducts()));
            }
        } catch (IOException ignored) {
            // Institution metadata is useful for display, but should not block linking.
        }
    }

    private void populateInstitutionMetadata(PlaidItem plaidItem) {
        if (!hasText(plaidItem.getInstitutionId())) {
            return;
        }

        try {
            InstitutionsGetByIdRequest request = new InstitutionsGetByIdRequest()
                    .institutionId(plaidItem.getInstitutionId())
                    .countryCodes(Arrays.asList(CountryCode.CA))
                    .options(new InstitutionsGetByIdRequestOptions().includeOptionalMetadata(true));

            Response<InstitutionsGetByIdResponse> response = plaidClient.institutionsGetById(request).execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getInstitution() == null) {
                return;
            }

            Institution institution = response.body().getInstitution();
            plaidItem.setInstitutionName(fallback(institution.getName(), plaidItem.getInstitutionName()));
            plaidItem.setInstitutionPrimaryColor(institution.getPrimaryColor());
            plaidItem.setInstitutionUrl(institution.getUrl());
            plaidItem.setInstitutionLogo(toDataUrl(institution.getLogo()));
        } catch (IOException ignored) {
            // Cached account data remains usable when institution metadata is unavailable.
        }
    }

    private InstitutionMetadataDto toInstitutionMetadataDto(Institution institution) {
        return new InstitutionMetadataDto(
                institution.getInstitutionId(),
                institution.getName(),
                toDataUrl(institution.getLogo()),
                institution.getPrimaryColor(),
                institution.getUrl());
    }

    private List<String> mergeRequestedCapabilities(
            List<String> requestedCapabilities,
            List<String> requestedProducts,
            List<String> requestedDataScopes) {
        Set<String> normalized = new LinkedHashSet<>();
        normalized.add("accounts");
        addNormalizedCapabilities(normalized, requestedCapabilities);
        addNormalizedCapabilities(normalized, requestedProducts);
        addNormalizedCapabilities(normalized, requestedDataScopes);

        if (normalized.size() == 1) {
            normalized.add("transactions");
        }

        return new ArrayList<>(normalized);
    }

    private void addNormalizedCapabilities(Set<String> target, List<String> rawValues) {
        if (rawValues == null) {
            return;
        }

        for (String rawValue : rawValues) {
            String normalized = normalizeCapability(rawValue);
            if (normalized != null) {
                target.add(normalized);
            }
        }
    }

    private String normalizeCapability(String value) {
        if (!hasText(value)) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT).replace("_", "-");
        return switch (normalized) {
            case "account", "accounts", "auth", "balance", "balances" -> "accounts";
            case "transaction", "transactions" -> "transactions";
            case "investment", "investments", "investment-transactions", "investment-holdings" -> "investments";
            case "asset", "assets" -> "assets";
            case "liability", "liabilities" -> "liabilities";
            default -> null;
        };
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }

        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String normalizedValue = normalizeCapability(value);
            if (normalizedValue != null && !normalized.contains(normalizedValue)) {
                normalized.add(normalizedValue);
            }
        }
        return normalized;
    }

    private List<String> filterProductCapabilities(List<String> capabilities) {
        List<String> products = new ArrayList<>();
        if (capabilities == null) {
            return products;
        }

        for (String capability : capabilities) {
            if (!"accounts".equals(capability)) {
                products.add(capability);
            }
        }
        return products;
    }

    private List<Products> requestedPlaidProducts(List<String> capabilities) {
        List<Products> products = new ArrayList<>();
        if (capabilities != null) {
            if (capabilities.contains("transactions")) {
                products.add(Products.TRANSACTIONS);
            }
            if (capabilities.contains("investments")) {
                products.add(Products.INVESTMENTS);
            }
            if (capabilities.contains("assets")) {
                products.add(Products.ASSETS);
            }
            if (capabilities.contains("liabilities")) {
                products.add(Products.LIABILITIES);
            }
        }

        if (products.isEmpty()) {
            products.add(Products.TRANSACTIONS);
        }
        return products;
    }

    private List<String> productValues(List<Products> products) {
        List<String> values = new ArrayList<>();
        for (Products product : products) {
            if (product != null && product.getValue() != null) {
                values.add(normalizePlaidProductValue(product.getValue()));
            }
        }
        return values;
    }

    private String normalizePlaidProductValue(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "auth", "identity", "balance", "balances" -> "accounts";
            case "transactions" -> "transactions";
            case "investments" -> "investments";
            case "assets" -> "assets";
            case "liabilities" -> "liabilities";
            default -> value.toLowerCase(Locale.ROOT);
        };
    }

    private boolean shouldSyncTransactions(PlaidItem plaidItem) {
        return requestedCapability(plaidItem, "transactions")
                && productEnabledOrUnknown(plaidItem, "transactions");
    }

    private boolean shouldSyncInvestments(PlaidItem plaidItem) {
        if (!requestedCapability(plaidItem, "investments")) {
            return false;
        }
        if (productEnabled(plaidItem, "investments")) {
            return true;
        }
        return productsUnknown(plaidItem) && hasInvestmentAccount(plaidItem);
    }

    private boolean requestedCapability(PlaidItem plaidItem, String capability) {
        List<String> requested = plaidItem.getRequestedCapabilities();
        if (requested == null || requested.isEmpty()) {
            return "accounts".equals(capability) || "transactions".equals(capability);
        }
        return requested.contains(capability);
    }

    private boolean productEnabledOrUnknown(PlaidItem plaidItem, String product) {
        return productsUnknown(plaidItem) || productEnabled(plaidItem, product);
    }

    private boolean productsUnknown(PlaidItem plaidItem) {
        return plaidItem.getEnabledProducts() == null || plaidItem.getEnabledProducts().isEmpty();
    }

    private boolean productEnabled(PlaidItem plaidItem, String product) {
        return plaidItem.getEnabledProducts() != null && plaidItem.getEnabledProducts().contains(product);
    }

    private boolean hasInvestmentAccount(PlaidItem plaidItem) {
        if (plaidItem.getUser() == null || plaidItem.getUser().getUserId() == null) {
            return false;
        }

        return accountRepo.findAllByUser_Id(plaidItem.getUser().getUserId()).stream()
                .filter(account -> samePlaidItem(account.getPlaidItem(), plaidItem))
                .anyMatch(account -> "investment".equalsIgnoreCase(account.getType())
                        || "investments".equalsIgnoreCase(account.getType()));
    }

    private boolean samePlaidItem(PlaidItem left, PlaidItem right) {
        return left != null
                && right != null
                && left.getItemId() != null
                && left.getItemId().equals(right.getItemId());
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return hasText(message) ? message : exception.getClass().getSimpleName();
    }

    private String toDataUrl(String logo) {
        if (!hasText(logo)) {
            return null;
        }
        if (logo.startsWith("data:image/")) {
            return logo;
        }
        return "data:image/png;base64," + logo;
    }

    private String getString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    private String formatMask(String mask) {
        if (mask == null || mask.isBlank()) {
            return "****";
        }

        if (mask.startsWith("****")) {
            return mask;
        }

        return "**** " + mask;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String fallback(String value, String fallback) {
        return hasText(value) ? value : fallback;
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
}
