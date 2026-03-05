package com.monetka.service;

import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository    transactionRepository;
    private final CategoryDetectionService detectionService;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryDetectionService detectionService) {
        this.transactionRepository = transactionRepository;
        this.detectionService      = detectionService;
    }

    @Transactional
    public Transaction addExpense(User user, BigDecimal amount, String description) {
        CategoryDetectionService.DetectionResult result =
                detectionService.detectCategory(description, user.getTelegramId());

        Transaction tx = new Transaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setDescription(description);
        tx.setType(TransactionType.EXPENSE);
        tx.setCategory(result.getCategory());
        tx.setSubcategory(result.getSubcategory());

        user.setBalance(user.getBalance().subtract(amount));

        log.info("Expense: user={} amount={} category={} confidence={}",
                user.getTelegramId(), amount,
                result.getCategory() != null ? result.getCategory().getName() : "—",
                result.getConfidence());

        return transactionRepository.save(tx);
    }

    @Transactional
    public Transaction addIncome(User user, BigDecimal amount, String description) {
        Transaction tx = new Transaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setDescription(description);
        tx.setType(TransactionType.INCOME);
        user.setBalance(user.getBalance().add(amount));
        return transactionRepository.save(tx);
    }
}