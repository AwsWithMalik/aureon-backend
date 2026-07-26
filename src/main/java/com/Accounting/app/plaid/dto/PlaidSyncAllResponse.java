package com.Accounting.app.plaid.dto;

import java.util.ArrayList;
import java.util.List;

public class PlaidSyncAllResponse {
    private boolean accountsSynced;
    private boolean transactionsSynced;
    private boolean investmentTransactionsSynced;
    private boolean investmentHoldingsSynced;
    private List<String> skipped = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public boolean isAccountsSynced() {
        return accountsSynced;
    }

    public void setAccountsSynced(boolean accountsSynced) {
        this.accountsSynced = accountsSynced;
    }

    public boolean isTransactionsSynced() {
        return transactionsSynced;
    }

    public void setTransactionsSynced(boolean transactionsSynced) {
        this.transactionsSynced = transactionsSynced;
    }

    public boolean isInvestmentTransactionsSynced() {
        return investmentTransactionsSynced;
    }

    public void setInvestmentTransactionsSynced(boolean investmentTransactionsSynced) {
        this.investmentTransactionsSynced = investmentTransactionsSynced;
    }

    public boolean isInvestmentHoldingsSynced() {
        return investmentHoldingsSynced;
    }

    public void setInvestmentHoldingsSynced(boolean investmentHoldingsSynced) {
        this.investmentHoldingsSynced = investmentHoldingsSynced;
    }

    public List<String> getSkipped() {
        return skipped;
    }

    public void setSkipped(List<String> skipped) {
        this.skipped = skipped == null ? new ArrayList<>() : skipped;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors == null ? new ArrayList<>() : errors;
    }

    public void skip(String message) {
        this.skipped.add(message);
    }

    public void error(String message) {
        this.errors.add(message);
    }
}
