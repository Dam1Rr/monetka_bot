package com.monetka.repository;

import com.monetka.model.Debt;
import com.monetka.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    /** Все активные (не закрытые) долги пользователя */
    @Query("SELECT d FROM Debt d WHERE d.user = :user AND d.closedAt IS NULL ORDER BY d.createdAt")
    List<Debt> findActiveByUser(@Param("user") User user);

    /** Все долги пользователя включая закрытые */
    @Query("SELECT d FROM Debt d WHERE d.user = :user ORDER BY d.closedAt NULLS FIRST, d.createdAt")
    List<Debt> findAllByUser(@Param("user") User user);

    /** Найти активный долг по триггер-слову (точное совпадение) */
    @Query("SELECT d FROM Debt d WHERE d.user = :user AND d.triggerWord = :trigger AND d.closedAt IS NULL")
    Optional<Debt> findByTrigger(@Param("user") User user, @Param("trigger") String trigger);

    /** Для статистики: сумма всех выплаченных по закрытым долгам */
    @Query("SELECT COALESCE(SUM(d.totalAmount), 0) FROM Debt d WHERE d.user = :user AND d.closedAt IS NOT NULL")
    java.math.BigDecimal sumClosedTotal(@Param("user") User user);
}