package com.monetka.service;

import com.monetka.model.Category;
import com.monetka.model.Subscription;
import com.monetka.model.User;
import com.monetka.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final CategoryDetectionService categoryDetectionService;

    @Transactional
    public Subscription create(User user, String name, BigDecimal amount, int dayOfMonth) {
        Category category = categoryDetectionService.detect(name);

        Subscription sub = Subscription.builder()
            .user(user)
            .name(name)
            .amount(amount)
            .category(category)
            .dayOfMonth(dayOfMonth)
            .active(true)
            .build();

        Subscription saved = subscriptionRepository.save(sub);
        log.info("Subscription created: '{}' {} for user {}", name, amount, user.getTelegramId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Subscription> getActiveSubscriptions(User user) {
        return subscriptionRepository.findByUserAndActiveTrue(user);
    }

    @Transactional
    public boolean cancel(User user, Long subscriptionId) {
        return subscriptionRepository.findById(subscriptionId).map(sub -> {
            if (!sub.getUser().getId().equals(user.getId())) return false;
            sub.setActive(false);
            subscriptionRepository.save(sub);
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<Subscription> getDueToday(int dayOfMonth) {
        return subscriptionRepository.findByActiveTrueAndDayOfMonth(dayOfMonth);
    }
}
