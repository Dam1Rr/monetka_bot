package com.monetka.repository;

import com.monetka.model.Subscription;
import com.monetka.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserAndActiveTrue(User user);

    /** Подписки которые сегодня начались (для ежемесячного списания) */
    @Query("SELECT s FROM Subscription s WHERE s.active = true " +
            "AND DAY(s.startDate) = DAY(:today) " +
            "AND (s.endDate IS NULL OR s.endDate >= :today)")
    List<Subscription> findDueToday(LocalDate today);

    /** Подписки истекающие в ближайшие N дней */
    @Query("SELECT s FROM Subscription s WHERE s.active = true " +
            "AND s.endDate IS NOT NULL " +
            "AND s.endDate BETWEEN :from AND :to")
    List<Subscription> findExpiringBetween(LocalDate from, LocalDate to);
}