package com.monetka.service;

import com.monetka.model.User;
import com.monetka.model.enums.UserStatus;
import com.monetka.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // ---- Registration ----

    @Transactional
    public User registerOrGet(Long telegramId, String username,
                              String firstName, String lastName) {
        return userRepository.findByTelegramId(telegramId).orElseGet(() -> {
            User newUser = User.builder()
                .telegramId(telegramId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .status(UserStatus.PENDING)
                .build();
            User saved = userRepository.save(newUser);
            log.info("New user registered: {} ({})", saved.getDisplayName(), telegramId);
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    @Transactional(readOnly = true)
    public boolean isActive(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
            .map(u -> u.getStatus() == UserStatus.ACTIVE)
            .orElse(false);
    }

    // ---- Admin actions ----

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

    @Transactional(readOnly = true)
    public List<User> getPendingUsers() {
        return userRepository.findAllByStatus(UserStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<User> getActiveUsers() {
        return userRepository.findAllByStatus(UserStatus.ACTIVE);
    }
}
