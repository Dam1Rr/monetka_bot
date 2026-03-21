package com.monetka.repository;

import com.monetka.model.User;
import com.monetka.model.UserReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserReminderRepository extends JpaRepository<UserReminder, Long> {

    Optional<UserReminder> findByUser(User user);

    /** Все включённые напоминания у которых утро включено и час совпадает */
    @Query("""
        SELECT r FROM UserReminder r
        JOIN FETCH r.user
        WHERE r.enabled = true
          AND r.morningEnabled = true
          AND r.hourMorning = :hour
    """)
    List<UserReminder> findEnabledMorningAt(@Param("hour") int hour);

    /** Все включённые напоминания у которых вечер включено и час совпадает */
    @Query("""
        SELECT r FROM UserReminder r
        JOIN FETCH r.user
        WHERE r.enabled = true
          AND r.eveningEnabled = true
          AND r.hourEvening = :hour
    """)
    List<UserReminder> findEnabledEveningAt(@Param("hour") int hour);
}