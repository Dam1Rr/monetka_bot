package com.monetka.repository;

import com.monetka.model.User;
import com.monetka.model.UserInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserInsightRepository extends JpaRepository<UserInsight, Long> {

    boolean existsByUserAndTriggerKeyAndMonthAndYear(
            User user, String triggerKey, int month, int year);

    @Query("""
        SELECT COUNT(i) FROM UserInsight i
        WHERE i.user = :user
          AND i.sentAt >= :since
    """)
    long countRecentInsights(
            @Param("user")  User user,
            @Param("since") LocalDateTime since);

    @Query("""
        SELECT COUNT(i) FROM UserInsight i
        WHERE i.user = :user
          AND i.sentAt >= :since
          AND i.triggerKey LIKE 'neg_%'
    """)
    long countRecentNegativeInsights(
            @Param("user")  User user,
            @Param("since") LocalDateTime since);
}