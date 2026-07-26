package com.Accounting.app.investments;

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
@Table(name = "investment_portfolio_snapshots")
public class InvestmentPortfolioSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String email;
    private String accountId;
    private String accountName;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalValue;

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
    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDateTime getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(LocalDateTime snapshotAt) { this.snapshotAt = snapshotAt; }
    public PlaidItem getPlaidItem() { return plaidItem; }
    public void setPlaidItem(PlaidItem plaidItem) { this.plaidItem = plaidItem; }
}
