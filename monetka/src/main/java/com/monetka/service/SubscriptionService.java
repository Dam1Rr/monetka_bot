package com.monetka.service;

import com.monetka.model.Category;
import com.monetka.model.Subscription;
import com.monetka.model.User;
import com.monetka.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository   subscriptionRepository;
    private final CategoryDetectionService categoryDetectionService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               CategoryDetectionService categoryDetectionService) {
        this.subscriptionRepository   = subscriptionRepository;
        this.categoryDetectionService = categoryDetectionService;
    }

    @Transactional
    public Subscription create(User user, String name, BigDecimal amount,
                               LocalDate startDate, LocalDate endDate) {
        Category category = categoryDetectionService
                .detectCategory(name, user.getTelegramId()).getCategory();

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setName(name);
        sub.setAmount(amount);
        sub.setCategory(category);
        sub.setStartDate(startDate);
        sub.setEndDate(endDate);
        sub.setActive(true);

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
    public List<Subscription> getDueToday() {
        return subscriptionRepository.findDueToday(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Subscription> getExpiringSoon(int days) {
        return subscriptionRepository.findExpiringBetween(LocalDate.now(), LocalDate.now().plusDays(days));
    }
}