package com.monetka.admin;

import com.monetka.model.User;
import com.monetka.model.enums.UserStatus;
import com.monetka.repository.TransactionRepository;
import com.monetka.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        LocalDateTime now        = LocalDateTime.now(BISHKEK);
        LocalDateTime todayStart = LocalDate.now(BISHKEK).atStartOfDay();
        LocalDateTime weekStart  = todayStart.minusDays(6);
        LocalDateTime monthStart = todayStart.withDayOfMonth(1);
        LocalDateTime month30    = todayStart.minusDays(29);

        // ── User counts ─────────────────────────────────────────────
        long totalUsers   = userRepository.countByStatus(UserStatus.ACTIVE);
        long pendingUsers = userRepository.countByStatus(UserStatus.PENDING);
        long blockedUsers = userRepository.countByStatus(UserStatus.BLOCKED);
        long churnedUsers = userRepository.countByBlockedBotTrue();

        // ── DAU / WAU / MAU ─────────────────────────────────────────
        long dau = transactionRepository.countActiveUsersInPeriod(todayStart, now);
        long wau = transactionRepository.countActiveUsersInPeriod(weekStart, now);
        long mau = transactionRepository.countActiveUsersInPeriodDistinct(month30);

        // ── Transactions ─────────────────────────────────────────────
        long txToday = transactionRepository.countTransactionsInPeriod(todayStart, now);
        long txWeek  = transactionRepository.countTransactionsInPeriod(weekStart, now);
        long txMonth = transactionRepository.countTransactionsInPeriod(monthStart, now);

        // ── Avg expense per user this month ──────────────────────────
        Object[] sumAndCount = transactionRepository.sumAndUserCountExpenses(monthStart, now);
        BigDecimal totalExp  = toBD(sumAndCount[0]);
        long       activeUsers = toLong(sumAndCount[1]);
        BigDecimal avgExpense  = activeUsers > 0
                ? totalExp.divide(BigDecimal.valueOf(activeUsers), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ── Inactive 3+ days ─────────────────────────────────────────
        List<Object[]> summary = transactionRepository.userActivitySummary();
        long inactive3 = 0;
        for (Object[] row : summary) {
            LocalDateTime last = (LocalDateTime) row[2];
            if (ChronoUnit.DAYS.between(last, now) >= 3) inactive3++;
        }

        // ── Top 5 users this week ─────────────────────────────────────
        List<Object[]> topRaw = transactionRepository.topUsersByActivityInPeriod(weekStart, now);
        List<UserActivity> top5 = new ArrayList<>();
        for (int i = 0; i < Math.min(5, topRaw.size()); i++) {
            User u   = (User) topRaw.get(i)[0];
            long cnt = (Long) topRaw.get(i)[1];
            top5.add(new UserActivity(u.getDisplayName(), cnt));
        }

        // ── Top categories this month (global) ───────────────────────
        List<Object[]> catRaw = transactionRepository.topCategoriesGlobal(monthStart, now);
        List<CategoryTrend> topCats = new ArrayList<>();
        for (int i = 0; i < Math.min(5, catRaw.size()); i++) {
            String name  = (String) catRaw.get(i)[0];
            String emoji = (String) catRaw.get(i)[1];
            BigDecimal total = toBD(catRaw.get(i)[2]);
            long cnt         = toLong(catRaw.get(i)[3]);
            topCats.add(new CategoryTrend(
                    (emoji != null ? emoji + " " : "") + name, total, cnt));
        }

        // ── Per-user retention table ──────────────────────────────────
        List<UserRetention> retention = new ArrayList<>();
        List<User> activeList = userRepository.findAllByStatus(UserStatus.ACTIVE);
        for (User u : activeList) {
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
                    daysAgo,
                    u.isBlockedBot()
            ));
        }
        retention.sort(Comparator.comparingLong(r -> -r.txCount));

        return new ActivitySnapshot(
                totalUsers, pendingUsers, blockedUsers, churnedUsers,
                dau, wau, mau,
                inactive3,
                txToday, txWeek, txMonth,
                avgExpense,
                top5, topCats, retention
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static BigDecimal toBD(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Double d)  return BigDecimal.valueOf(d);
        if (v instanceof Float  f)  return BigDecimal.valueOf((double) f);
        if (v instanceof Long   l)  return BigDecimal.valueOf(l);
        if (v instanceof Integer i) return BigDecimal.valueOf(i);
        if (v instanceof Number n)  return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString().trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Long l) return l;
        return ((Number) v).longValue();
    }

    // ── DTOs ─────────────────────────────────────────────────────────

    public record ActivitySnapshot(
            long totalUsers, long pendingUsers, long blockedUsers, long churnedUsers,
            long dau, long wau, long mau,
            long inactive3days,
            long txToday, long txWeek, long txMonth,
            BigDecimal avgExpensePerUser,
            List<UserActivity>  top5,
            List<CategoryTrend> topCategories,
            List<UserRetention> retention
    ) {}

    public record UserActivity(String name, long txCount) {}

    public record CategoryTrend(String label, BigDecimal total, long txCount) {}

    public record UserRetention(
            String name, String username,
            java.time.LocalDate registeredAt,
            long txCount,
            java.time.LocalDate lastActivity,
            long daysAgo,
            boolean blockedBot
    ) {}
}