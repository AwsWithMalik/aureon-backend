package com.Accounting.app.accounts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.Accounting.app.auth.User;
import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.transactions.Transaction;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "Accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    
    private String accountId;
    private String accountName;
    @Column(precision = 19, scale = 2)
    private BigDecimal balance;
    @Column(precision = 19, scale = 2)
    private BigDecimal availableBalance;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "account")
    @JsonIgnore
    private List<Transaction> transactions = new ArrayList<>();

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "userId")
    private User user;

    private String email;
    private String unofficialName;

    @ManyToOne
    @JoinColumn(name = "plaid_item_id")
    private PlaidItem plaidItem;

    private String plaidAccountId;
    private String officialName;
    private String type;
    private String subtype;
    private String mask;
    private LocalDateTime dateAdded;
    private LocalDateTime lastSyncAttemptAt;
    private LocalDateTime lastSyncSuccessAt;
    private LocalDateTime lastSyncFailureAt;
    @Column(length = 1000)
    private String lastSyncError;
    @Column(precision = 19, scale = 2)
    private BigDecimal previousBalance;
    @Column(precision = 19, scale = 2)
    private BigDecimal previousAvailableBalance;

    public Account(String accountName, BigDecimal balance, LocalDateTime createdAt, String email,
            String plaidAccountId, String officialName, String type, String subtype, String mask, String accountId,
            String unofficialName, LocalDateTime dateAdded) {
        this.accountName = accountName;
        this.balance = balance;
        this.createdAt = createdAt;
        this.email = email;
        this.plaidAccountId = plaidAccountId;
        this.officialName = officialName;
        this.type = type;
        this.subtype = subtype;
        this.mask = mask;
        this.accountId = accountId;
        this.dateAdded = dateAdded;
        this.unofficialName = unofficialName;
    }

    public Account() {

    }

    public Integer getId() {
        return id;
    }

    public String getAccountName() {
        return accountName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public PlaidItem getPlaidItem() {
        return plaidItem;
    }

    public void setPlaidItem(PlaidItem plaidItem) {
        this.plaidItem = plaidItem;
    }

    public String getPlaidAccountId() {
        return plaidAccountId;
    }

    public void setPlaidAccountId(String plaidAccountId) {
        this.plaidAccountId = plaidAccountId;
    }

    public String getOfficialName() {
        return officialName;
    }

    public void setOfficialName(String officialName) {
        this.officialName = officialName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubtype() {
        return subtype;
    }

    public void setSubtype(String subtype) {
        this.subtype = subtype;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setDateAdded(LocalDateTime date) {
        this.dateAdded = date;
    }

    public LocalDateTime getDateAdded() {
        return dateAdded;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public void setUnofficialName(String name) {
        this.unofficialName = name;
    }

    public String getUnofficialName() {
        return unofficialName;
    }
    public LocalDateTime getLastSyncAttemptAt() {
        return lastSyncAttemptAt;
    }

    public void setLastSyncAttemptAt(LocalDateTime lastSyncAttemptAt) {
        this.lastSyncAttemptAt = lastSyncAttemptAt;
    }

    public LocalDateTime getLastSyncSuccessAt() {
        return lastSyncSuccessAt;
    }

    public void setLastSyncSuccessAt(LocalDateTime lastSyncSuccessAt) {
        this.lastSyncSuccessAt = lastSyncSuccessAt;
    }

    public LocalDateTime getLastSyncFailureAt() {
        return lastSyncFailureAt;
    }

    public void setLastSyncFailureAt(LocalDateTime lastSyncFailureAt) {
        this.lastSyncFailureAt = lastSyncFailureAt;
    }

    public String getLastSyncError() {
        return lastSyncError;
    }

    public void setLastSyncError(String lastSyncError) {
        this.lastSyncError = lastSyncError;
    }

    public BigDecimal getPreviousBalance() {
        return previousBalance;
    }

    public void setPreviousBalance(BigDecimal previousBalance) {
        this.previousBalance = previousBalance;
    }

    public BigDecimal getPreviousAvailableBalance() {
        return previousAvailableBalance;
    }

    public void setPreviousAvailableBalance(BigDecimal previousAvailableBalance) {
        this.previousAvailableBalance = previousAvailableBalance;
    }
}


