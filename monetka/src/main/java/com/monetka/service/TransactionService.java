package com.monetka.service;

import com.monetka.model.Category;
import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import com.monetka.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository        userRepository;
    private final CategoryDetectionService categoryDetectionService;

    // ---- Add expense ----

    @Transactional
    public Transaction addExpense(User user, BigDecimal amount, String description) {
        Category category = categoryDetectionService.detect(description);

        Transaction tx = Transaction.builder()
            .user(user)
            .amount(amount)
            .description(description)
            .category(category)
            .type(TransactionType.EXPENSE)
            .build();

        transactionRepository.save(tx);

        // Update balance
        user.setBalance(user.getBalance().subtract(amount));
        userRepository.save(user);

        log.debug("Expense saved: {} {} → {} [{}]",
            amount, description, category.getName(), user.getTelegramId());
        return tx;
    }

    // ---- Add income ----

    @Transactional
    public Transaction addIncome(User user, BigDecimal amount, String description) {
        Transaction tx = Transaction.builder()
            .user(user)
            .amount(amount)
            .description(description)
            .type(TransactionType.INCOME)
            .build();

        transactionRepository.save(tx);

        user.setBalance(user.getBalance().add(amount));
        userRepository.save(user);

        log.debug("Income saved: {} {} [{}]", amount, description, user.getTelegramId());
        return tx;
    }

    // ---- Queries ----

    @Transactional(readOnly = true)
    public List<Transaction> getTodayExpenses(User user) {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to   = from.plusDays(1);
        return transactionRepository.findByUserAndTypeAndPeriod(
            user, TransactionType.EXPENSE, from, to);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getMonthExpenses(User user) {
        LocalDateTime from = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);
        return transactionRepository.findByUserAndTypeAndPeriod(
            user, TransactionType.EXPENSE, from, to);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTodayTotal(User user, TransactionType type) {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to   = from.plusDays(1);
        return transactionRepository.sumByUserAndTypeAndPeriod(user, type, from, to);
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthTotal(User user, TransactionType type) {
        LocalDateTime from = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);
        return transactionRepository.sumByUserAndTypeAndPeriod(user, type, from, to);
    }
}
