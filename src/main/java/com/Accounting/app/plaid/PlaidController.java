package com.Accounting.app.plaid;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.investments.dto.InvestmentHoldingsResponse;
import com.Accounting.app.investments.dto.InvestmentTransactionDto;
import com.Accounting.app.investments.dto.InvestmentTransactionsResponse;
import com.Accounting.app.auth.Config;
import com.Accounting.app.plaid.dto.InstitutionMetadataDto;
import com.Accounting.app.plaid.dto.PlaidLinkTokenCreateRequest;
import com.Accounting.app.plaid.dto.PlaidSyncAllResponse;
import com.Accounting.app.plaid.dto.PublicTokenExchangeRequest;
import com.Accounting.app.accounts.AccountService;
import com.Accounting.app.investments.PlaidInvestmentService;
import com.plaid.client.model.AccountBase;

import org.springframework.web.bind.annotation.RequestBody;

@RestController
public class PlaidController {
    private final PlaidServices plaidServices;
    private final PlaidInvestmentService plaidInvestmentService;
    private final AccountService accountService;
    private final Config config;

    public PlaidController(
            PlaidServices plaidServices,
            PlaidInvestmentService plaidInvestmentService,
            AccountService accountService,
            Config config) {
        this.plaidServices = plaidServices;
        this.plaidInvestmentService = plaidInvestmentService;
        this.accountService = accountService;
        this.config = config;
    }

    @PostMapping("/link/token/create")
    public ResponseEntity<String> createLinkToken(@RequestBody(required = false) PlaidLinkTokenCreateRequest request)
            throws IOException {
        return ResponseEntity.ok(plaidServices.createLinkToken(config.getEmail(), request));
    }

    @PostMapping("/item/public_token/exchange")
    public ResponseEntity<String> exchangePublicToken(@RequestBody PublicTokenExchangeRequest request)
            throws IOException {
        return ResponseEntity.ok(plaidServices.exchangePublicToken(
                config.getEmail(),
                request.getPublicToken(),
                request.getInstitutionId(),
                request.getInstitutionName(),
                request.getRequestedCapabilities(),
                request.getRequestedProducts(),
                request.getRequestedDataScopes()));
    }

    @GetMapping("/api/accounts/get")
    public ResponseEntity<List<AccountBase>> getBankAccounts() {
        return ResponseEntity.ok(accountService.saveAccounts(config.getEmail()));
    }

    @GetMapping("/api/plaid/institutions/{institutionId}")
    public ResponseEntity<InstitutionMetadataDto> getInstitutionById(@PathVariable String institutionId)
            throws IOException {
        return ResponseEntity.ok(plaidServices.getInstitutionById(institutionId));
    }

    @GetMapping("/api/plaid/institutions/search")
    public ResponseEntity<List<InstitutionMetadataDto>> searchInstitutions(@RequestParam String query)
            throws IOException {
        return ResponseEntity.ok(plaidServices.searchInstitutions(query));
    }

    @PostMapping("/api/transactions/sync")
    public ResponseEntity<List<com.plaid.client.model.Transaction>> syncTransactions() throws IOException {

        return ResponseEntity.ok(plaidServices.syncTransactions(config.getEmail()));
    }

    @PostMapping("/api/plaid/sync-all")
    public ResponseEntity<PlaidSyncAllResponse> syncAll() {
        return ResponseEntity.ok(plaidServices.syncAll(config.getEmail()));
    }

    @GetMapping("/api/investments/transactions/get")
    public ResponseEntity<InvestmentTransactionsResponse> getInvestmentTransactions() throws IOException {
        return ResponseEntity.ok(plaidInvestmentService.getInvestmentTransactions(config.getEmail()));
    }

    @PostMapping("/api/investments/transactions/sync")
    public ResponseEntity<List<InvestmentTransactionDto>> syncInvestmentTransactions() throws IOException {
        return ResponseEntity.ok(plaidInvestmentService.syncInvestmentTransactionLogs(config.getEmail()));
    }

    @GetMapping("/api/investments/holdings/get")
    public ResponseEntity<InvestmentHoldingsResponse> getInvestmentHoldings() throws IOException {
        return ResponseEntity.ok(plaidInvestmentService.getInvestmentHoldings(config.getEmail()));
    }

    @PostMapping("/api/plaid/webhook")
    public ResponseEntity<Void> plaidWebhook(@RequestBody Map<String, Object> payload) throws IOException {
        plaidServices.handlePlaidWebhook(payload);
        return ResponseEntity.ok().build();
    }

}
