package com.monetka.service;

import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository  transactionRepository;
    private final CategoryDetectionService detectionService;

    @Transactional
    public Transaction addExpense(User user, BigDecimal amount, String description) {
        CategoryDetectionService.DetectionResult result =
                detectionService.detectCategory(description, user.getTelegramId());

        Transaction tx = Transaction.builder()
                .user(user)
                .amount(amount)
                .description(description)
                .type(TransactionType.EXPENSE)
                .category(result.getCategory())
                .subcategory(result.getSubcategory())
                .build();

        user.setBalance(user.getBalance().subtract(amount));

        log.info("Expense: user={} amount={} category={} subcategory={} confidence={}",
                user.getTelegramId(), amount,
                result.getCategory() != null ? result.getCategory().getName() : "—",
                result.getSubcategory() != null ? result.getSubcategory().getName() : "—",
                result.getConfidence());

        return transactionRepository.save(tx);
    }

    @Transactional
    public Transaction addIncome(User user, BigDecimal amount, String description) {
        Transaction tx = Transaction.builder()
                .user(user)
                .amount(amount)
                .description(description)
                .type(TransactionType.INCOME)
                .build();

        user.setBalance(user.getBalance().add(amount));
        return transactionRepository.save(tx);
    }

    /**
     * Вызывается когда пользователь вручную выбрал категорию.
     * Обновляет транзакцию и обучает систему.
     */
    @Transactional
    public void correctCategory(Transaction tx,
                                com.monetka.model.Category category,
                                com.monetka.model.Subcategory subcategory,
                                String keyword) {
        tx.setCategory(category);
        tx.setSubcategory(subcategory);
        transactionRepository.save(tx);

        // Обучаем
        if (keyword != null && !keyword.isBlank()) {
            detectionService.learnKeyword(keyword, category, subcategory,
                    tx.getUser().getTelegramId());
        }
    }
}