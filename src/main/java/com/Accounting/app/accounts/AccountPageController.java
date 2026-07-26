package com.Accounting.app.accounts;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.accounts.dto.AccountsPageResponse;

@RestController
public class AccountPageController {
    private final AccountPageServices accountPageServices;
    private final Config config;

    public AccountPageController(AccountPageServices accountPageServices,Config config) {
        this.accountPageServices = accountPageServices;
        this.config= config;
    }

    @GetMapping("/api/dashboard/accounts")
    public ResponseEntity<AccountsPageResponse> getAccountsInfo() {
        return ResponseEntity.ok(accountPageServices.accountsPageResponse(config.getEmail()));

    }
}
