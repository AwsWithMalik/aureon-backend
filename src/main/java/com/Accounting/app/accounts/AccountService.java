package com.Accounting.app.accounts;

import java.util.List;

import org.springframework.stereotype.Service;

import com.Accounting.app.plaid.PlaidServices;
import com.plaid.client.model.AccountBase;

@Service
public class AccountService {
    private final PlaidServices plaidServices;

    public AccountService(PlaidServices plaidServices) {
        this.plaidServices = plaidServices;
    }

    public List<AccountBase> saveAccounts(String email) {
        return plaidServices.refreshAccounts(email);
    }
}
