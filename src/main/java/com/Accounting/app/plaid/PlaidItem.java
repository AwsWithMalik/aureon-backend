package com.Accounting.app.plaid;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

import com.Accounting.app.accounts.Account;
import com.Accounting.app.auth.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Entity
public class PlaidItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer itemId;

    private String institutionName;
    private String institutionId;
    @Column(columnDefinition = "TEXT")
    private String institutionLogo;
    private String institutionPrimaryColor;
    private String institutionUrl;

    @OneToMany(mappedBy = "plaidItem")
    private List<Account> accounts = new ArrayList<>();
    private String syncCursor;
    @Column(unique = true)
    private String plaidItemId;

    @Column(nullable = false)
    private String encryptedAccessToken;
    private LocalDateTime lastInvestmentSyncAttemptAt;
    private LocalDateTime lastInvestmentSyncSuccessAt;
    private LocalDateTime lastInvestmentSyncFailureAt;
    @Column(length = 1000)
    private String lastInvestmentSyncError;
    private LocalDateTime lastAccountSyncAttemptAt;
    private LocalDateTime lastAccountSyncSuccessAt;
    private LocalDateTime lastAccountSyncFailureAt;
    @Column(length = 1000)
    private String lastAccountSyncError;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> requestedCapabilities = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> requestedProducts = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> requestedDataScopes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> enabledProducts = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> availableProducts = new ArrayList<>();

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public PlaidItem() {

    }

    public PlaidItem(String institutionName, List<Account> accounts, String encryptedAccessToken, String syncCursor) {
        this.institutionName = institutionName;
        this.accounts = accounts;
        this.encryptedAccessToken = encryptedAccessToken;
        this.syncCursor = syncCursor;
    }

    public Integer getItemId() {
        return itemId;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

    public String getInstitutionLogo() {
        return institutionLogo;
    }

    public void setInstitutionLogo(String institutionLogo) {
        this.institutionLogo = institutionLogo;
    }

    public String getInstitutionPrimaryColor() {
        return institutionPrimaryColor;
    }

    public void setInstitutionPrimaryColor(String institutionPrimaryColor) {
        this.institutionPrimaryColor = institutionPrimaryColor;
    }

    public String getInstitutionUrl() {
        return institutionUrl;
    }

    public void setInstitutionUrl(String institutionUrl) {
        this.institutionUrl = institutionUrl;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccount(List<Account> accounts) {
        this.accounts = accounts;
    }

    public void setInstituationName(String institutionName) {
        this.institutionName = institutionName;
    }

    public void setAccessToken(String encryptedAccessToken) {
        this.encryptedAccessToken = encryptedAccessToken;
    }

    public void setSyncCursor(String cursor) {
        this.syncCursor = cursor;
    }

    public String getAccessToken() {
        return encryptedAccessToken;
    }

    public String getSyncCursor() {
        return syncCursor;
    }

    public String getPlaidItemId() {
        return plaidItemId;
    }

    public void setPlaidItemId(String plaidItemId) {
        this.plaidItemId = plaidItemId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getLastInvestmentSyncAttemptAt() {
        return lastInvestmentSyncAttemptAt;
    }

    public void setLastInvestmentSyncAttemptAt(LocalDateTime lastInvestmentSyncAttemptAt) {
        this.lastInvestmentSyncAttemptAt = lastInvestmentSyncAttemptAt;
    }

    public LocalDateTime getLastInvestmentSyncSuccessAt() {
        return lastInvestmentSyncSuccessAt;
    }

    public void setLastInvestmentSyncSuccessAt(LocalDateTime lastInvestmentSyncSuccessAt) {
        this.lastInvestmentSyncSuccessAt = lastInvestmentSyncSuccessAt;
    }

    public LocalDateTime getLastInvestmentSyncFailureAt() {
        return lastInvestmentSyncFailureAt;
    }

    public void setLastInvestmentSyncFailureAt(LocalDateTime lastInvestmentSyncFailureAt) {
        this.lastInvestmentSyncFailureAt = lastInvestmentSyncFailureAt;
    }

    public String getLastInvestmentSyncError() {
        return lastInvestmentSyncError;
    }

    public void setLastInvestmentSyncError(String lastInvestmentSyncError) {
        this.lastInvestmentSyncError = lastInvestmentSyncError;
    }
    public LocalDateTime getLastAccountSyncAttemptAt() {
        return lastAccountSyncAttemptAt;
    }

    public void setLastAccountSyncAttemptAt(LocalDateTime lastAccountSyncAttemptAt) {
        this.lastAccountSyncAttemptAt = lastAccountSyncAttemptAt;
    }

    public LocalDateTime getLastAccountSyncSuccessAt() {
        return lastAccountSyncSuccessAt;
    }

    public void setLastAccountSyncSuccessAt(LocalDateTime lastAccountSyncSuccessAt) {
        this.lastAccountSyncSuccessAt = lastAccountSyncSuccessAt;
    }

    public LocalDateTime getLastAccountSyncFailureAt() {
        return lastAccountSyncFailureAt;
    }

    public void setLastAccountSyncFailureAt(LocalDateTime lastAccountSyncFailureAt) {
        this.lastAccountSyncFailureAt = lastAccountSyncFailureAt;
    }

    public String getLastAccountSyncError() {
        return lastAccountSyncError;
    }

    public void setLastAccountSyncError(String lastAccountSyncError) {
        this.lastAccountSyncError = lastAccountSyncError;
    }

    public List<String> getRequestedCapabilities() {
        return requestedCapabilities;
    }

    public void setRequestedCapabilities(List<String> requestedCapabilities) {
        this.requestedCapabilities = requestedCapabilities == null ? new ArrayList<>() : requestedCapabilities;
    }

    public List<String> getRequestedProducts() {
        return requestedProducts;
    }

    public void setRequestedProducts(List<String> requestedProducts) {
        this.requestedProducts = requestedProducts == null ? new ArrayList<>() : requestedProducts;
    }

    public List<String> getRequestedDataScopes() {
        return requestedDataScopes;
    }

    public void setRequestedDataScopes(List<String> requestedDataScopes) {
        this.requestedDataScopes = requestedDataScopes == null ? new ArrayList<>() : requestedDataScopes;
    }

    public List<String> getEnabledProducts() {
        return enabledProducts;
    }

    public void setEnabledProducts(List<String> enabledProducts) {
        this.enabledProducts = enabledProducts == null ? new ArrayList<>() : enabledProducts;
    }

    public List<String> getAvailableProducts() {
        return availableProducts;
    }

    public void setAvailableProducts(List<String> availableProducts) {
        this.availableProducts = availableProducts == null ? new ArrayList<>() : availableProducts;
    }
}


