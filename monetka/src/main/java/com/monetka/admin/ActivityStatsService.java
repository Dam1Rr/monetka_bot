package com.monetka.admin;

import com.monetka.model.User;
import com.monetka.model.enums.UserStatus;
import com.monetka.repository.TransactionRepository;
import com.monetka.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ActivityStatsService {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private final UserRepository        userRepository;
    private final TransactionRepository transactionRepository;

    public ActivityStatsService(UserRepository userRepository,
                                TransactionRepository transactionRepository) {
        this.userRepository        = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public ActivitySnapshot getSnapshot() {
        LocalDateTime now       = LocalDateTime.now(BISHKEK);
        LocalDateTime todayStart = LocalDate.now(BISHKEK).atStartOfDay();
        LocalDateTime weekStart  = todayStart.minusDays(6);

        long totalUsers   = userRepository.countByStatus(UserStatus.ACTIVE);
        long pendingUsers = userRepository.countByStatus(UserStatus.PENDING);
        long blockedUsers = userRepository.countByStatus(UserStatus.BLOCKED);

        long activeToday  = transactionRepository.countActiveUsersInPeriod(todayStart, now);
        long activeWeek   = transactionRepository.countActiveUsersInPeriod(weekStart, now);

        long txToday = transactionRepository.countTransactionsInPeriod(todayStart, now);
        long txWeek  = transactionRepository.countTransactionsInPeriod(weekStart, now);

        // Users inactive 3+ days
        List<Object[]> summary = transactionRepository.userActivitySummary();
        long inactive3 = 0;
        for (Object[] row : summary) {
            LocalDateTime last = (LocalDateTime) row[2];
            if (ChronoUnit.DAYS.between(last, now) >= 3) inactive3++;
        }

        // Top 5 active users this week
        List<Object[]> topRaw = transactionRepository.topUsersByActivityInPeriod(weekStart, now);
        List<UserActivity> top5 = new ArrayList<>();
        for (int i = 0; i < Math.min(5, topRaw.size()); i++) {
            User u    = (User) topRaw.get(i)[0];
            long cnt  = (Long) topRaw.get(i)[1];
            top5.add(new UserActivity(u.getDisplayName(), cnt));
        }

        // Per-user retention table
        List<UserRetention> retention = new ArrayList<>();
        List<User> activeUsers = userRepository.findAllByStatus(UserStatus.ACTIVE);
        for (User u : activeUsers) {
            long txCount = 0;
            LocalDateTime lastTx = null;
            for (Object[] row : summary) {
                if (((User) row[0]).getId().equals(u.getId())) {
                    txCount = (Long) row[1];
                    lastTx  = (LocalDateTime) row[2];
                    break;
                }
            }
            long daysAgo = lastTx != null ? ChronoUnit.DAYS.between(lastTx, now) : -1;
            retention.add(new UserRetention(
                    u.getDisplayName(),
                    u.getUsername(),
                    u.getCreatedAt() != null ? u.getCreatedAt().toLocalDate() : null,
                    txCount,
                    lastTx != null ? lastTx.toLocalDate() : null,
                    daysAgo
            ));
        }
        retention.sort(Comparator.comparingLong(r -> -r.txCount));

        return new ActivitySnapshot(
                totalUsers, pendingUsers, blockedUsers,
                activeToday, activeWeek, inactive3,
                txToday, txWeek,
                top5, retention
        );
    }

    // ── DTOs ──────────────────────────────────────────────────────────

    public record ActivitySnapshot(
            long totalUsers, long pendingUsers, long blockedUsers,
            long activeToday, long activeWeek, long inactive3days,
            long txToday, long txWeek,
            List<UserActivity> top5,
            List<UserRetention> retention
    ) {}

    public record UserActivity(String name, long txCount) {}

    public record UserRetention(
            String name, String username,
            LocalDate registeredAt,
            long txCount,
            LocalDate lastActivity,
            long daysAgo
    ) {}
}