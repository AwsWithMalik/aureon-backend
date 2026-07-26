package com.Accounting.app.transactions;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.Accounting.app.accounts.Account;

@Service
public class TransactionClassificationService {

    public String resolvePlaidCategoryPrimary(com.plaid.client.model.Transaction plaidTransaction) {
        if (plaidTransaction.getPersonalFinanceCategory() != null
                && plaidTransaction.getPersonalFinanceCategory().getPrimary() != null) {
            return plaidTransaction.getPersonalFinanceCategory().getPrimary();
        }
        return null;
    }

    public String resolvePlaidCategoryDetailed(com.plaid.client.model.Transaction plaidTransaction) {
        if (plaidTransaction.getPersonalFinanceCategory() != null
                && plaidTransaction.getPersonalFinanceCategory().getDetailed() != null) {
            return plaidTransaction.getPersonalFinanceCategory().getDetailed();
        }
        return null;
    }

    public String resolveDisplayCategory(com.plaid.client.model.Transaction transaction, Account account) {
        if (isTransfer(transaction, account)) {
            return "Transfer";
        }

        String combined = plaidText(transaction);

        if (combined.contains("aws") || combined.contains("amazon web services")) {
            return "Software";
        }

        if (combined.contains("google play")) {
            return "Digital Purchases";
        }

        String plaidPrimary = safe(resolvePlaidCategoryPrimary(transaction));
        if (plaidPrimary.contains("INCOME")) {
            return "Income";
        }

        return "Uncategorized";
    }

    public String resolveDisplayMerchantName(com.plaid.client.model.Transaction transaction, Account account) {
        String text = plaidText(transaction);

        if (isCreditCardPayment(text, account)) {
            return "Credit Card Payment";
        }

        if (text.contains("interest")) {
            return "Interest Payment";
        }

        if (text.contains("interac") || text.contains("e-transfer")) {
            if (text.contains("deposit") || text.contains("transfer in")) {
                return "E-Transfer Deposit";
            }
            return "E-Transfer";
        }

        if (text.contains("transfer in")) {
            return "Incoming Transfer";
        }

        if (text.contains("transfer out")) {
            return "Outgoing Transfer";
        }

        if (transaction.getMerchantName() != null && !transaction.getMerchantName().isBlank()) {
            return transaction.getMerchantName();
        }

        return transaction.getName() != null ? transaction.getName() : "Unknown Merchant";
    }

    public TransactionType resolveTransactionType(com.plaid.client.model.Transaction transaction, Account account) {
        if (isTransfer(transaction, account)) {
            return TransactionType.TRANSFER;
        }

        String primaryCategory = safe(resolvePlaidCategoryPrimary(transaction));
        if (primaryCategory.contains("INCOME")) {
            return TransactionType.INCOME;
        }

        Double amount = transaction.getAmount();
        if (amount != null && amount > 0) {
            return TransactionType.EXPENSE;
        }

        return TransactionType.INCOME;
    }

    public boolean isTransfer(com.plaid.client.model.Transaction transaction, Account account) {
        String primary = safe(resolvePlaidCategoryPrimary(transaction));
        String detailed = safe(resolvePlaidCategoryDetailed(transaction));
        String text = plaidText(transaction) + " " + primary + " " + detailed;

        return isPlaidTransferCategory(primary, detailed)
                || text.contains("transfer in")
                || text.contains("transfer out")
                || text.contains("account transfer")
                || text.contains("interac")
                || text.contains("e-transfer")
                || isCreditCardPayment(text, account);
    }

    public boolean normalizeStoredTransaction(Transaction transaction) {
        if (transaction == null) {
            return false;
        }

        boolean transfer = isStoredTransfer(transaction);
        boolean changed = false;

        if (transfer && transaction.getType() != TransactionType.TRANSFER) {
            transaction.setTransactionType(TransactionType.TRANSFER);
            changed = true;
        }

        if (transfer && !transaction.isTransfer()) {
            transaction.setTransfer(true);
            changed = true;
        }

        if (transfer && transaction.isIncludedInCashFlow()) {
            transaction.setIncludedInCashFlow(false);
            changed = true;
        }

        if (transfer && !"Transfer".equals(transaction.getDisplayCategory())) {
            transaction.setDisplayCategory("Transfer");
            changed = true;
        }

        if (transfer && transaction.isTaxRelevant()) {
            transaction.setTaxRelevant(false);
            changed = true;
        }

        if (transfer && transaction.isNeedsReview()) {
            transaction.setNeedsReview(false);
            transaction.setReviewReason(null);
            changed = true;
        }

        if (transfer && isCreditCardPayment(storedText(transaction), transaction.getAccount())
                && !"Credit Card Payment".equals(transaction.getMerchantName())) {
            transaction.setMerchantName("Credit Card Payment");
            changed = true;
        }

        return changed;
    }

    public boolean isStoredTransfer(Transaction transaction) {
        if (transaction.getType() == TransactionType.TRANSFER || transaction.isTransfer()) {
            return true;
        }

        String primary = safe(transaction.getPlaidCategoryPrimary());
        String detailed = safe(transaction.getPlaidCategoryDetailed());
        String text = storedText(transaction) + " " + primary + " " + detailed;

        return isPlaidTransferCategory(primary, detailed)
                || text.contains("transfer in")
                || text.contains("transfer out")
                || text.contains("account transfer")
                || text.contains("interac")
                || text.contains("e-transfer")
                || isCreditCardPayment(text, transaction.getAccount());
    }

    public boolean shouldIncludeInCashFlow(TransactionType type) {
        return type != TransactionType.TRANSFER;
    }

    private boolean isPlaidTransferCategory(String primary, String detailed) {
        return primary.contains("TRANSFER")
                || detailed.contains("TRANSFER")
                || detailed.contains("CREDIT_CARD_PAYMENT")
                || detailed.contains("LOAN_PAYMENTS_CREDIT_CARD_PAYMENT");
    }

    private boolean isCreditCardPayment(String text, Account account) {
        boolean paymentPhrase = text.contains("credit card payment")
                || text.contains("card payment")
                || text.contains("payment - thank you")
                || text.contains("payment thank you")
                || text.contains("online banking payment")
                || text.contains("automatic payment")
                || text.contains("autopay")
                || text.contains("cc payment")
                || text.contains("cc pmt")
                || text.contains("card pmt");

        if (paymentPhrase) {
            return true;
        }

        boolean accountLooksLikeBankFundingSource = isDepositoryAccount(account);
        boolean textLooksLikeCardBrand = text.contains(" visa")
                || text.startsWith("visa")
                || text.contains("mastercard")
                || text.contains("master card")
                || text.contains("amex")
                || text.contains("american express")
                || text.contains("credit card");

        boolean textLooksLikeFinancialInstitution = text.contains(" td ")
                || text.startsWith("td ")
                || text.contains("rbc")
                || text.contains("royal bank")
                || text.contains("scotia")
                || text.contains("cibc")
                || text.contains("bmo")
                || text.contains("capital one")
                || text.contains("mbna")
                || text.contains("desjardins")
                || text.contains("tangerine");

        return accountLooksLikeBankFundingSource && textLooksLikeCardBrand && textLooksLikeFinancialInstitution;
    }

    private boolean isDepositoryAccount(Account account) {
        if (account == null) {
            return false;
        }

        String type = safe(account.getType());
        String subtype = safe(account.getSubtype());
        return type.contains("depository")
                || subtype.contains("checking")
                || subtype.contains("chequing")
                || subtype.contains("savings");
    }

    private String plaidText(com.plaid.client.model.Transaction transaction) {
        return String.join(" ",
                safe(transaction.getName()),
                safe(transaction.getMerchantName()),
                safe(transaction.getWebsite()));
    }

    private String storedText(Transaction transaction) {
        return String.join(" ",
                safe(transaction.getDescription()),
                safe(transaction.getRawMerchantName()),
                safe(transaction.getMerchantName()),
                safe(transaction.getPlaidName()),
                safe(transaction.getWebsite()),
                safe(transaction.getDisplayCategory()),
                safe(transaction.getPaymentChannel()),
                amountText(transaction.getAmount()));
    }

    private String amountText(BigDecimal amount) {
        return amount == null ? "" : amount.toPlainString();
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
