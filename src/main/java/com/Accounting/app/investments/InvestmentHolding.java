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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "investment_holdings", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "plaid_item_id", "accountId", "securityId" })
})
public class InvestmentHolding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "plaid_item_id")
    private PlaidItem plaidItem;

    private String accountId;
    private String accountName;
    private String securityId;
    private String securityName;
    private String ticker;
    @Column(precision = 19, scale = 6)
    private BigDecimal quantity;
    @Column(precision = 19, scale = 4)
    private BigDecimal institutionPrice;
    @Column(precision = 19, scale = 2)
    private BigDecimal institutionValue;
    private String currency;
    private LocalDateTime syncedAt;

    public Integer getId() {
        return id;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getInstitutionPrice() {
        return institutionPrice;
    }

    public void setInstitutionPrice(BigDecimal institutionPrice) {
        this.institutionPrice = institutionPrice;
    }

    public BigDecimal getInstitutionValue() {
        return institutionValue;
    }

    public void setInstitutionValue(BigDecimal institutionValue) {
        this.institutionValue = institutionValue;
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
