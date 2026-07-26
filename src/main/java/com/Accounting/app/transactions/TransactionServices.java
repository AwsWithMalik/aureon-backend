package com.Accounting.app.transactions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.Accounting.app.transactions.dto.MonthlyReportResponse;


@Service
public class TransactionServices {
    private final TransactionsRepo transactionsRepo;

    public TransactionServices(TransactionsRepo transactionsRepo) {
        this.transactionsRepo = transactionsRepo;
    }

    public List<Transaction> getTransactionsByAccountId(Integer accountId) {
        return transactionsRepo.findByAccountId(accountId);
    }

    public List<Transaction> getTransactionsByCategory(String category) {
        return transactionsRepo.findByDisplayCategoryIgnoreCase(category);
    }

    public List<Transaction> getTransactionsBetween(LocalDateTime start, LocalDateTime finish) {
        return transactionsRepo.findByTimestampBetween(start, finish);
    }

    public BigDecimal getTotalIncome(Integer accountId) {
        return transactionsRepo.findByAccountId(accountId)
                .stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalExpense(Integer accountId) {
        return transactionsRepo.findByAccountId(accountId)
                .stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getNetCashFlow(Integer accountId) {
        BigDecimal income = getTotalIncome(accountId);
        BigDecimal expense = getTotalExpense(accountId);

        BigDecimal netCashFlow = income.subtract(expense);

        return netCashFlow;
    }

    public BigDecimal getTotalByCategory(Integer accountId, String category) {
        return transactionsRepo.findByAccountId(accountId)
                .stream()
                .filter(t -> t.getDisplayCategory() != null && t.getDisplayCategory().equalsIgnoreCase(category))
                .map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Transaction> getTransactionsForMonth(Integer accountId, int year, int month) {
        return transactionsRepo.findByAccountId(accountId)
                .stream()
                .filter(t -> t.getTimestamp().getYear() == year
                        &&
                        t.getTimestamp().getMonthValue() == month)

                .toList();
    }

    private BigDecimal getTotalByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(t -> t.getType() == type)
                .map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public MonthlyReportResponse getMonthlyReport(Integer accountId, int year, int month) {
        List<Transaction> transactions = getTransactionsForMonth(accountId, year, month);

        BigDecimal income = getTotalByType(transactions, TransactionType.INCOME);

        BigDecimal expense = getTotalByType(transactions, TransactionType.EXPENSE);

        BigDecimal netCashFlow = income.subtract(expense);

        return new MonthlyReportResponse(income, expense, netCashFlow);
    }

    public Map<String, BigDecimal> getCategoryBreakdown(Integer accountId) {
        return transactionsRepo.findByAccountId(accountId)
                .stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(t -> t.getDisplayCategory() == null ? "Uncategorized" : t.getDisplayCategory(),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add))

                );
    }

    
}
