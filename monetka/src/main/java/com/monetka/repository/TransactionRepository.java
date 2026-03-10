package com.monetka.repository;

import com.monetka.model.Category;
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

    /** Sum expenses for a specific category in a period */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user = :user
          AND t.type = 'EXPENSE'
          AND t.category = :category
          AND t.createdAt BETWEEN :from AND :to
    """)
    BigDecimal sumByUserCategoryAndPeriod(
            @Param("user")     User user,
            @Param("category") Category category,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to);

    /** Simple category totals — used for daily report */
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

    /** Detailed breakdown by category + subcategory — used for /stats */
    @Query("""
        SELECT t.category.name,
               t.category.emoji,
               sc.name,
               SUM(t.amount)
        FROM Transaction t LEFT JOIN t.subcategory sc
        WHERE t.user = :user
          AND t.type = 'EXPENSE'
          AND t.createdAt BETWEEN :from AND :to
        GROUP BY t.category.name, t.category.emoji, sc.name
        ORDER BY SUM(t.amount) DESC
    """)
    List<Object[]> sumExpensesByCategoryAndSubcategoryAndPeriod(
            @Param("user") User user,
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);

    /** Recent transactions in a category for drill-down */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user = :user
          AND t.type = 'EXPENSE'
          AND t.category.id = :categoryId
          AND t.createdAt BETWEEN :from AND :to
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findExpensesByCategoryAndPeriod(
            @Param("user")       User user,
            @Param("categoryId") Long categoryId,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to);

    /** Recent transactions in a subcategory for drill-down */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user = :user
          AND t.type = 'EXPENSE'
          AND t.subcategory.id = :subcategoryId
          AND t.createdAt BETWEEN :from AND :to
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findExpensesBySubcategoryAndPeriod(
            @Param("user")          User user,
            @Param("subcategoryId") Long subcategoryId,
            @Param("from")          LocalDateTime from,
            @Param("to")            LocalDateTime to);

    /** Subcategory totals within a category */
    @Query("""
        SELECT t.subcategory.name, t.subcategory.emoji, SUM(t.amount)
        FROM Transaction t
        WHERE t.user = :user
          AND t.type = 'EXPENSE'
          AND t.category.id = :categoryId
          AND t.createdAt BETWEEN :from AND :to
          AND t.subcategory IS NOT NULL
        GROUP BY t.subcategory.name, t.subcategory.emoji
        ORDER BY SUM(t.amount) DESC
    """)
    List<Object[]> sumBySubcategoryInCategory(
            @Param("user")       User user,
            @Param("categoryId") Long categoryId,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to);

    /** Global sum of all expenses — admin statistics */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'EXPENSE'")
    BigDecimal sumAllExpenses();

    // ================================================================
    // Activity monitoring queries
    // ================================================================

    /** Count distinct users who had transactions in a period */
    @Query("""
        SELECT COUNT(DISTINCT t.user.id)
        FROM Transaction t
        WHERE t.createdAt BETWEEN :from AND :to
    """)
    long countActiveUsersInPeriod(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);

    /** Count total transactions in a period */
    @Query("""
        SELECT COUNT(t)
        FROM Transaction t
        WHERE t.createdAt BETWEEN :from AND :to
    """)
    long countTransactionsInPeriod(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);

    /** Top N active users by transaction count in a period */
    @Query("""
        SELECT t.user, COUNT(t) as cnt
        FROM Transaction t
        WHERE t.createdAt BETWEEN :from AND :to
        GROUP BY t.user
        ORDER BY cnt DESC
    """)
    List<Object[]> topUsersByActivityInPeriod(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);

    /** Count transactions per user — for retention table */
    @Query("""
        SELECT t.user, COUNT(t) as cnt,
               MAX(t.createdAt) as lastActivity
        FROM Transaction t
        GROUP BY t.user
        ORDER BY lastActivity DESC
    """)
    List<Object[]> userActivitySummary();

    /** Delete all transactions for a user — used when starting fresh after onboarding */
    void deleteByUser(User user);

    /** MAU — distinct users with any transaction in last 30 days */
    @Query("""
        SELECT COUNT(DISTINCT t.user)
        FROM Transaction t
        WHERE t.createdAt >= :from
    """)
    long countActiveUsersInPeriodDistinct(@Param("from") LocalDateTime from);

    /** Sum of expenses in period */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.type = 'EXPENSE' AND t.createdAt BETWEEN :from AND :to
    """)
    BigDecimal sumExpensesInPeriod(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);

    /** Count distinct users with expenses in period */
    @Query("""
        SELECT COUNT(DISTINCT t.user)
        FROM Transaction t
        WHERE t.type = 'EXPENSE' AND t.createdAt BETWEEN :from AND :to
    """)
    long countUsersWithExpenses(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);

    /** Top categories by total spend across ALL users in period */
    @Query("""
        SELECT t.category.name, t.category.emoji,
               SUM(t.amount) as total, COUNT(t) as cnt
        FROM Transaction t
        WHERE t.type = 'EXPENSE'
          AND t.category IS NOT NULL
          AND t.createdAt BETWEEN :from AND :to
        GROUP BY t.category.name, t.category.emoji
        ORDER BY total DESC
    """)
    List<Object[]> topCategoriesGlobal(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM Transaction t WHERE t.user = :user")
    void deleteAllByUser(@Param("user") com.monetka.model.User user);
}