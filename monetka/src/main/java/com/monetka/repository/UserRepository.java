package com.monetka.repository;

import com.monetka.model.User;
import com.monetka.model.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    boolean existsByTelegramId(Long telegramId);

    List<User> findAllByStatus(UserStatus status);

    /** For admin statistics panel */
    long countByStatus(UserStatus status);

    /** All active users who haven't blocked the bot — for broadcast */
    java.util.List<User> findAllByStatusAndBlockedBotFalse(UserStatus status);

    /** Count churned (blocked bot) users */
    long countByBlockedBotTrue();
}