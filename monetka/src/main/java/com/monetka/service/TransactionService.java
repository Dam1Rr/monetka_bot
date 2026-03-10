package com.monetka.service;

import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import com.monetka.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository    transactionRepository;
    private final UserRepository           userRepository;
    private final CategoryDetectionService detectionService;

    public TransactionService(TransactionRepository transactionRepository,
                              UserRepository userRepository,
                              CategoryDetectionService detectionService) {
        this.transactionRepository = transactionRepository;
        this.userRepository        = userRepository;
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

        // BUG FIX: update balance AND persist the user
        user.setBalance(user.getBalance().subtract(amount));
        userRepository.save(user);

        log.info("Expense: user={} amount={} category={} confidence={}",
                user.getTelegramId(), amount,
                result.getCategory() != null ? result.getCategory().getName() : "—",
                result.getConfidence());

        return transactionRepository.save(tx);
    }

    /** Save expense with explicit category — skips auto-detection */
    @Transactional
    public Transaction addExpense(User user, BigDecimal amount, String description,
                                  com.monetka.model.Category category,
                                  com.monetka.model.Subcategory subcategory) {
        Transaction tx = new Transaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setDescription(description);
        tx.setType(com.monetka.model.enums.TransactionType.EXPENSE);
        tx.setCategory(category);
        tx.setSubcategory(subcategory);

        user.setBalance(user.getBalance().subtract(amount));
        userRepository.save(user);

        log.info("Expense (manual cat): user={} amount={} category={}",
                user.getTelegramId(), amount,
                category != null ? category.getName() : "—");

        return transactionRepository.save(tx);
    }

    @Transactional
    public Transaction addIncome(User user, BigDecimal amount, String description) {
        Transaction tx = new Transaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setDescription(description);
        tx.setType(TransactionType.INCOME);

        // BUG FIX: persist balance update
        user.setBalance(user.getBalance().add(amount));
        userRepository.save(user);

        return transactionRepository.save(tx);
    }
}