package com.Accounting.app.accounts;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.Accounting.app.plaid.PlaidItem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_balance_snapshots")
public class AccountBalanceSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String email;
    private String accountId;
    private String accountName;
    private String accountType;
    private String accountSubtype;

    @Column(precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(precision = 19, scale = 2)
    private BigDecimal availableBalance;

    private String currency;
    private LocalDateTime snapshotAt;

    @ManyToOne
    @JoinColumn(name = "plaid_item_id")
    private PlaidItem plaidItem;

    public Integer getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getAccountSubtype() { return accountSubtype; }
    public void setAccountSubtype(String accountSubtype) { this.accountSubtype = accountSubtype; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDateTime getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(LocalDateTime snapshotAt) { this.snapshotAt = snapshotAt; }
    public PlaidItem getPlaidItem() { return plaidItem; }
    public void setPlaidItem(PlaidItem plaidItem) { this.plaidItem = plaidItem; }
}
