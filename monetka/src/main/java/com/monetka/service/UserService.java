package com.monetka.service;

import com.monetka.model.User;
import com.monetka.model.enums.UserStatus;
import com.monetka.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User registerOrGet(Long telegramId, String username, String firstName, String lastName) {
        return userRepository.findByTelegramId(telegramId).orElseGet(() -> {
            User saved = userRepository.save(User.create(telegramId, username, firstName, lastName));
            log.info("New user registered: {} ({})", saved.getDisplayName(), telegramId);
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean isActive(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .map(u -> u.getStatus() == UserStatus.ACTIVE).orElse(false);
    }

    @Transactional
    public boolean approveUser(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).map(user -> {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
            log.info("User approved: {}", telegramId);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean blockUser(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).map(user -> {
            user.setStatus(UserStatus.BLOCKED);
            userRepository.save(user);
            log.info("User blocked: {}", telegramId);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean unblockUser(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).map(user -> {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
            log.info("User unblocked: {}", telegramId);
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<User> getPendingUsers()  { return userRepository.findAllByStatus(UserStatus.PENDING); }

    @Transactional(readOnly = true)
    public List<User> getActiveUsers() { return userRepository.findAllByStatus(UserStatus.ACTIVE); }

    @Transactional(readOnly = true)
    public List<User> getBlockedUsers()  { return userRepository.findAllByStatus(UserStatus.BLOCKED); }
}