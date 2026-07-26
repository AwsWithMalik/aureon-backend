package com.Accounting.app.transactions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.accounts.Account;

@Service
public class TransferMatchingService {
    private static final int MATCHED_THRESHOLD = 75;
    private static final int POSSIBLE_THRESHOLD = 55;
    private static final BigDecimal EXACT_AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal LOOSE_AMOUNT_TOLERANCE = new BigDecimal("1.00");

    private final TransactionsRepo transactionsRepo;
    private final TransactionClassificationService classificationService;

    public TransferMatchingService(
            TransactionsRepo transactionsRepo,
            TransactionClassificationService classificationService) {
        this.transactionsRepo = transactionsRepo;
        this.classificationService = classificationService;
    }

    @Transactional
    public void matchForUser(Integer userId) {
        if (userId == null) {
            return;
        }
        matchTransactions(transactionsRepo.findByAccount_User_Id(userId));
    }

    @Transactional
    public void matchAllUsers() {
        Map<Integer, List<Transaction>> byUser = transactionsRepo.findAll().stream()
                .filter(transaction -> transaction.getAccount() != null)
                .filter(transaction -> transaction.getAccount().getUser() != null)
                .collect(Collectors.groupingBy(transaction -> transaction.getAccount().getUser().getUserId()));

        byUser.values().forEach(this::matchTransactions);
    }

    private void matchTransactions(List<Transaction> transactions) {
        List<Transaction> candidates = transactions.stream()
                .filter(this::canParticipateInMatching)
                .sorted(Comparator.comparing(Transaction::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Set<Integer> matchedIds = candidates.stream()
                .filter(transaction -> "matched".equalsIgnoreCase(transaction.getTransferMatchStatus()))
                .map(Transaction::getTransactionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        for (Transaction transaction : candidates) {
            Integer transactionId = transaction.getTransactionId();
            if (transactionId == null || matchedIds.contains(transactionId)) {
                continue;
            }

            Optional<TransferMatch> bestMatch = candidates.stream()
                    .filter(candidate -> !Objects.equals(candidate.getTransactionId(), transactionId))
                    .filter(candidate -> !matchedIds.contains(candidate.getTransactionId()))
                    .map(candidate -> score(transaction, candidate))
                    .filter(match -> match.score() >= POSSIBLE_THRESHOLD)
                    .max(Comparator.comparingInt(TransferMatch::score));

            if (bestMatch.isEmpty()) {
                continue;
            }

            TransferMatch match = bestMatch.get();
            if (match.score() >= MATCHED_THRESHOLD) {
                markMatched(match.left(), match.right(), match);
                matchedIds.add(match.left().getTransactionId());
                matchedIds.add(match.right().getTransactionId());
            } else {
                markPossible(match.left(), match);
                markPossible(match.right(), match);
            }
        }

        transactionsRepo.saveAll(candidates);
    }

    private boolean canParticipateInMatching(Transaction transaction) {
        if (transaction == null || transaction.getTransactionId() == null) {
            return false;
        }
        if (Boolean.FALSE.equals(transaction.getUserConfirmedTransfer())) {
            return false;
        }
        if (transaction.getAccount() == null || transaction.getAccount().getId() == null) {
            return false;
        }
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        return transaction.getTimestamp() != null;
    }

    private TransferMatch score(Transaction left, Transaction right) {
        if (sameAccount(left, right)) {
            return new TransferMatch(left, right, 0, "Same account");
        }

        BigDecimal amountDifference = left.getAmount().abs().subtract(right.getAmount().abs()).abs();
        if (amountDifference.compareTo(LOOSE_AMOUNT_TOLERANCE) > 0) {
            return new TransferMatch(left, right, 0, "Amounts do not match");
        }

        long daysApart = Math.abs(Duration.between(
                left.getTimestamp().toLocalDate().atStartOfDay(),
                right.getTimestamp().toLocalDate().atStartOfDay()).toDays());
        if (daysApart > 5) {
            return new TransferMatch(left, right, 0, "Dates are too far apart");
        }

        boolean leftTransferSignal = hasTransferSignal(left);
        boolean rightTransferSignal = hasTransferSignal(right);
        boolean depositoryToCreditCardPair = isDepositoryToCreditCardPair(left.getAccount(), right.getAccount());
        if (!leftTransferSignal && !rightTransferSignal) {
            return new TransferMatch(left, right, 0, "Neither transaction has a transfer signal");
        }
        if (depositoryToCreditCardPair && !creditCardSideHasTransferSignal(left, right)) {
            return new TransferMatch(left, right, 0, "Credit-card side does not look like a payment");
        }

        int score = 0;
        StringBuilder reason = new StringBuilder();

        if (amountDifference.compareTo(EXACT_AMOUNT_TOLERANCE) <= 0) {
            score += 45;
            reason.append("amounts match exactly");
        } else {
            score += 25;
            reason.append("amounts are close");
        }

        if (daysApart == 0) {
            score += 25;
            reason.append("; same day");
        } else if (daysApart <= 2) {
            score += 20;
            reason.append("; dates within 2 days");
        } else {
            score += 12;
            reason.append("; dates within 5 days");
        }

        if (oppositeSigns(left, right)) {
            score += 10;
            reason.append("; opposite money direction");
        }

        if (depositoryToCreditCardPair) {
            score += 25;
            reason.append("; depository-to-credit-card pair");
        } else if (sameInstitution(left.getAccount(), right.getAccount())) {
            score += 8;
            reason.append("; same institution");
        }

        if (leftTransferSignal) {
            score += 12;
            reason.append("; left has transfer signal");
        }
        if (rightTransferSignal) {
            score += 12;
            reason.append("; right has transfer signal");
        }
        if (looksLikeCardPayment(left) || looksLikeCardPayment(right)) {
            score += 12;
            reason.append("; card-payment text");
        }

        return new TransferMatch(left, right, Math.min(score, 100), reason.toString());
    }

    private boolean hasTransferSignal(Transaction transaction) {
        return classificationService.isStoredTransfer(transaction) || looksLikeCardPayment(transaction);
    }

    private boolean creditCardSideHasTransferSignal(Transaction left, Transaction right) {
        if (isCreditCard(left.getAccount())) {
            return hasTransferSignal(left);
        }
        if (isCreditCard(right.getAccount())) {
            return hasTransferSignal(right);
        }
        return false;
    }

    private void markMatched(Transaction left, Transaction right, TransferMatch match) {
        String groupId = hasText(left.getTransferGroupId()) ? left.getTransferGroupId()
                : hasText(right.getTransferGroupId()) ? right.getTransferGroupId()
                        : "trf_" + UUID.randomUUID().toString().replace("-", "");

        applyMatchedTransfer(left, right.getTransactionId(), groupId, match);
        applyMatchedTransfer(right, left.getTransactionId(), groupId, match);
    }

    private void applyMatchedTransfer(
            Transaction transaction,
            Integer matchedTransactionId,
            String groupId,
            TransferMatch match) {
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setTransfer(true);
        transaction.setIncludedInCashFlow(false);
        transaction.setDisplayCategory("Transfer");
        transaction.setTaxRelevant(false);
        transaction.setNeedsReview(false);
        transaction.setReviewReason(null);
        transaction.setTransferGroupId(groupId);
        transaction.setMatchedTransferTransactionId(matchedTransactionId);
        transaction.setTransferMatchStatus("matched");
        transaction.setTransferMatchConfidence(confidence(match.score()));
        transaction.setTransferMatchReason(match.reason());
    }

    private void markPossible(Transaction transaction, TransferMatch match) {
        if ("matched".equalsIgnoreCase(transaction.getTransferMatchStatus())) {
            return;
        }
        transaction.setTransferMatchStatus("possible_match");
        transaction.setTransferMatchConfidence(confidence(match.score()));
        transaction.setTransferMatchReason(match.reason());
    }

    private Double confidence(int score) {
        return BigDecimal.valueOf(score)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private boolean sameAccount(Transaction left, Transaction right) {
        return left.getAccount() != null
                && right.getAccount() != null
                && Objects.equals(left.getAccount().getId(), right.getAccount().getId());
    }

    private boolean oppositeSigns(Transaction left, Transaction right) {
        return left.getAmount().signum() != right.getAmount().signum();
    }

    private boolean isDepositoryToCreditCardPair(Account left, Account right) {
        return (isDepository(left) && isCreditCard(right)) || (isDepository(right) && isCreditCard(left));
    }

    private boolean isDepository(Account account) {
        String type = safe(account == null ? null : account.getType());
        String subtype = safe(account == null ? null : account.getSubtype());
        return type.contains("depository")
                || subtype.contains("checking")
                || subtype.contains("chequing")
                || subtype.contains("savings");
    }

    private boolean isCreditCard(Account account) {
        String type = safe(account == null ? null : account.getType());
        String subtype = safe(account == null ? null : account.getSubtype());
        String name = safe(account == null ? null : account.getAccountName());
        return type.contains("credit")
                || subtype.contains("credit card")
                || name.contains("visa")
                || name.contains("mastercard")
                || name.contains("master card")
                || name.contains("amex")
                || name.contains("american express");
    }

    private boolean sameInstitution(Account left, Account right) {
        if (left == null || right == null || left.getPlaidItem() == null || right.getPlaidItem() == null) {
            return false;
        }
        return Objects.equals(left.getPlaidItem().getItemId(), right.getPlaidItem().getItemId());
    }

    private boolean looksLikeCardPayment(Transaction transaction) {
        String text = String.join(" ",
                safe(transaction.getDescription()),
                safe(transaction.getMerchantName()),
                safe(transaction.getRawMerchantName()),
                safe(transaction.getPlaidName()),
                safe(transaction.getDisplayCategory()));
        return text.contains("credit card payment")
                || text.contains("card payment")
                || text.contains("payment - thank you")
                || text.contains("payment thank you")
                || text.contains("td visa")
                || text.contains("visa")
                || text.contains("mastercard")
                || text.contains("amex");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record TransferMatch(Transaction left, Transaction right, int score, String reason) {
    }
}
