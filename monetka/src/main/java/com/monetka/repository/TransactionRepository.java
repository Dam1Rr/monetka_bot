package com.monetka.repository;

import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserOrderByCreatedAtDesc(User user);

    List<Transaction> findByUserAndTypeOrderByCreatedAtDesc(User user, TransactionType type);

    List<Transaction> findByUserAndCreatedAtBetweenOrderByCreatedAtDesc(
            User user, LocalDateTime from, LocalDateTime to);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user = :user
          AND t.type = :type
          AND t.createdAt BETWEEN :from AND :to
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findByUserAndTypeAndPeriod(
            @Param("user") User user,
            @Param("type") TransactionType type,
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user = :user
          AND t.type = :type
          AND t.createdAt BETWEEN :from AND :to
    """)
    BigDecimal sumByUserAndTypeAndPeriod(
            @Param("user") User user,
            @Param("type") TransactionType type,
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);

    @Query("""
        SELECT t.category.name, t.category.emoji, SUM(t.amount)
        FROM Transaction t
        WHERE t.user = :user
          AND t.type = 'EXPENSE'
          AND t.createdAt BETWEEN :from AND :to
        GROUP BY t.category.name, t.category.emoji
        ORDER BY SUM(t.amount) DESC
    """)
    List<Object[]> sumExpensesByCategoryAndPeriod(
            @Param("user") User user,
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);

    /** Global sum of all expenses — used in com.monetka.admin statistics */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'EXPENSE'")
    BigDecimal sumAllExpenses();
}