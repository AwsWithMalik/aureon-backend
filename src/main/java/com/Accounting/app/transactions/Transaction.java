package com.Accounting.app.transactions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.Accounting.app.accounts.Account;
import com.Accounting.app.files.UploadedFile;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.ConstraintMode;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer transactionId;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @Column(length = 500)
    private String description;

    private LocalDateTime timestamp;

    private String plaidTransactionId;

    private boolean pending;

    // Raw Plaid merchant fields
    private String rawMerchantName;
    private String plaidName;
    private String website;
    private String logoUrl;

    // Clean UI merchant field
    private String displayMerchantName;

    // Raw Plaid category fields
    private String plaidCategoryPrimary;
    private String plaidCategoryDetailed;
    private String plaidCategoryConfidence;

    // Clean UI category fields
    private String displayCategory;
    private String displaySubcategory;

    // Useful Plaid/payment fields
    private String plaidAccountId;
    private String isoCurrencyCode;
    private String paymentChannel;

    // App-level review/intelligence flags
    private boolean transfer;
    private boolean includedInCashFlow = true;
    private boolean taxRelevant;
    private boolean needsReview;

    private String reviewReason;
    private String transferGroupId;
    private Integer matchedTransferTransactionId;
    private String transferMatchStatus;
    private Double transferMatchConfidence;
    @Column(length = 1000)
    private String transferMatchReason;
    private Boolean userConfirmedTransfer;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @JsonIgnore
    @OneToMany(mappedBy = "relatedTransaction")
    private List<UploadedFile> receipts = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "transaction_metadata",
            joinColumns = @JoinColumn(name = "transaction_transaction_id", referencedColumnName = "transactionId"),
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    @Column(name = "metadata")
    private List<String> metadata = new ArrayList<>();

    public Transaction() {
    }

    public Transaction(BigDecimal amount, TransactionType transactionType, String description,
            LocalDateTime timestamp, Account account, String displayCategory, List<String> metadata,
            String plaidTransactionId, boolean pending, String rawMerchantName) {
        this.amount = amount;
        this.transactionType = transactionType;
        this.description = description;
        this.timestamp = timestamp;
        this.account = account;
        this.displayCategory = displayCategory;
        this.metadata = metadata;
        this.plaidTransactionId = plaidTransactionId;
        this.pending = pending;
        this.rawMerchantName = rawMerchantName;
    }

    public Integer getId() {
        return transactionId;
    }

    public Integer getTransactionId() {
        return transactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return transactionType;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getPlaidTransactionId() {
        return plaidTransactionId;
    }

    public boolean isPending() {
        return pending;
    }

    public String getRawMerchantName() {
        return rawMerchantName;
    }

    public String getPlaidName() {
        return plaidName;
    }

    public String getWebsite() {
        return website;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getDisplayMerchantName() {
        return displayMerchantName;
    }

    public String getMerchantName() {
        return displayMerchantName != null ? displayMerchantName : rawMerchantName;
    }

    public String getPlaidCategoryPrimary() {
        return plaidCategoryPrimary;
    }

    public String getPlaidCategoryDetailed() {
        return plaidCategoryDetailed;
    }

    public String getPlaidCategoryConfidence() {
        return plaidCategoryConfidence;
    }

    public String getDisplayCategory() {
        return displayCategory;
    }

    public String getCategory() {
        return displayCategory;
    }

    public String getDisplaySubcategory() {
        return displaySubcategory;
    }

    public String getPlaidAccountId() {
        return plaidAccountId;
    }

    public String getIsoCurrencyCode() {
        return isoCurrencyCode;
    }

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public boolean isTransfer() {
        return transfer;
    }

    public boolean isIncludedInCashFlow() {
        return includedInCashFlow;
    }

    public boolean isTaxRelevant() {
        return taxRelevant;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public String getTransferGroupId() {
        return transferGroupId;
    }

    public Integer getMatchedTransferTransactionId() {
        return matchedTransferTransactionId;
    }

    public String getTransferMatchStatus() {
        return transferMatchStatus;
    }

    public Double getTransferMatchConfidence() {
        return transferMatchConfidence;
    }

    public String getTransferMatchReason() {
        return transferMatchReason;
    }

    public Boolean getUserConfirmedTransfer() {
        return userConfirmedTransfer;
    }

    public Account getAccount() {
        return account;
    }

    public List<UploadedFile> getReceipts() {
        return receipts;
    }

    public List<String> getMetadata() {
        return metadata;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public void setPlaidTransactionId(String plaidTransactionId) {
        this.plaidTransactionId = plaidTransactionId;
    }

    public void setPending(boolean pending) {
        this.pending = pending;
    }

    public void setRawMerchantName(String rawMerchantName) {
        this.rawMerchantName = rawMerchantName;
    }

    public void setPlaidName(String plaidName) {
        this.plaidName = plaidName;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public void setDisplayMerchantName(String displayMerchantName) {
        this.displayMerchantName = displayMerchantName;
    }

    public void setMerchantName(String merchantName) {
        this.displayMerchantName = merchantName;
    }

    public void setPlaidCategoryPrimary(String plaidCategoryPrimary) {
        this.plaidCategoryPrimary = plaidCategoryPrimary;
    }

    public void setPlaidCategoryDetailed(String plaidCategoryDetailed) {
        this.plaidCategoryDetailed = plaidCategoryDetailed;
    }

    public void setPlaidCategoryConfidence(String plaidCategoryConfidence) {
        this.plaidCategoryConfidence = plaidCategoryConfidence;
    }

    public void setDisplayCategory(String displayCategory) {
        this.displayCategory = displayCategory;
    }

    public void setCategory(String category) {
        this.displayCategory = category;
    }

    public void setDisplaySubcategory(String displaySubcategory) {
        this.displaySubcategory = displaySubcategory;
    }

    public void setPlaidAccountId(String plaidAccountId) {
        this.plaidAccountId = plaidAccountId;
    }

    public void setIsoCurrencyCode(String isoCurrencyCode) {
        this.isoCurrencyCode = isoCurrencyCode;
    }

    public void setPaymentChannel(String paymentChannel) {
        this.paymentChannel = paymentChannel;
    }

    public void setTransfer(boolean transfer) {
        this.transfer = transfer;
    }

    public void setIncludedInCashFlow(boolean includedInCashFlow) {
        this.includedInCashFlow = includedInCashFlow;
    }

    public void setTaxRelevant(boolean taxRelevant) {
        this.taxRelevant = taxRelevant;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public void setTransferGroupId(String transferGroupId) {
        this.transferGroupId = transferGroupId;
    }

    public void setMatchedTransferTransactionId(Integer matchedTransferTransactionId) {
        this.matchedTransferTransactionId = matchedTransferTransactionId;
    }

    public void setTransferMatchStatus(String transferMatchStatus) {
        this.transferMatchStatus = transferMatchStatus;
    }

    public void setTransferMatchConfidence(Double transferMatchConfidence) {
        this.transferMatchConfidence = transferMatchConfidence;
    }

    public void setTransferMatchReason(String transferMatchReason) {
        this.transferMatchReason = transferMatchReason;
    }

    public void setUserConfirmedTransfer(Boolean userConfirmedTransfer) {
        this.userConfirmedTransfer = userConfirmedTransfer;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public void setReceipts(List<UploadedFile> receipts) {
        this.receipts = receipts;
    }

    public void setMetadata(List<String> metadata) {
        this.metadata = metadata;
    }
}
