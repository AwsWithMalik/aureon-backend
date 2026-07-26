package com.Accounting.app.investments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.Accounting.app.plaid.PlaidItem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "investment_transactions")
public class InvestmentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true)
    private String plaidInvestmentTransactionId;

    @ManyToOne
    @JoinColumn(name = "plaid_item_id")
    private PlaidItem plaidItem;

    private String accountId;
    private String accountName;
    private String securityId;
    private String securityName;
    private String ticker;

    @Enumerated(EnumType.STRING)
    private InvestmentTransactionType type;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(precision = 19, scale = 6)
    private BigDecimal quantity;
    @Column(precision = 19, scale = 4)
    private BigDecimal price;
    private LocalDate date;
    private String currency;
    private LocalDateTime syncedAt;

    public Integer getId() {
        return id;
    }

    public String getPlaidInvestmentTransactionId() {
        return plaidInvestmentTransactionId;
    }

    public void setPlaidInvestmentTransactionId(String plaidInvestmentTransactionId) {
        this.plaidInvestmentTransactionId = plaidInvestmentTransactionId;
    }

    public PlaidItem getPlaidItem() {
        return plaidItem;
    }

    public void setPlaidItem(PlaidItem plaidItem) {
        this.plaidItem = plaidItem;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getSecurityId() {
        return securityId;
    }

    public void setSecurityId(String securityId) {
        this.securityId = securityId;
    }

    public String getSecurityName() {
        return securityName;
    }

    public void setSecurityName(String securityName) {
        this.securityName = securityName;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public InvestmentTransactionType getType() {
        return type;
    }

    public void setType(InvestmentTransactionType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }
}
