package com.Accounting.app.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.transactions.TransactionClassificationService;
import com.Accounting.app.transactions.TransferMatchingService;
import com.Accounting.app.transactions.TransactionsRepo;

@Component
public class TransactionClassificationBackfill implements ApplicationRunner {
    private final TransactionsRepo transactionsRepo;
    private final TransactionClassificationService transactionClassificationService;
    private final TransferMatchingService transferMatchingService;

    public TransactionClassificationBackfill(
            TransactionsRepo transactionsRepo,
            TransactionClassificationService transactionClassificationService,
            TransferMatchingService transferMatchingService) {
        this.transactionsRepo = transactionsRepo;
        this.transactionClassificationService = transactionClassificationService;
        this.transferMatchingService = transferMatchingService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Transaction> changedTransactions = transactionsRepo.findAll().stream()
                .filter(transactionClassificationService::normalizeStoredTransaction)
                .toList();

        if (!changedTransactions.isEmpty()) {
            transactionsRepo.saveAll(changedTransactions);
        }
        transferMatchingService.matchAllUsers();
    }
}
