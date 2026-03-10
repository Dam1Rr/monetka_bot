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
        BigDecimal totalExp   = safe(() -> transactionRepository.sumExpensesInPeriod(monthStart, now));
        long       activeUsers = 0;
        try { activeUsers = transactionRepository.countUsersWithExpenses(monthStart, now); } catch (Exception ignored) {}
        BigDecimal avgExpense  = activeUsers > 0
                ? totalExp.divide(BigDecimal.valueOf(activeUsers), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ── Inactive 3+ days ─────────────────────────────────────────
        List<Object[]> summary;
        try { summary = transactionRepository.userActivitySummary(); }
        catch (Exception e) { summary = java.util.Collections.emptyList(); }
        long inactive3 = 0;
        for (Object[] row : summary) {
            try {
                if (row.length > 2 && row[2] instanceof LocalDateTime last) {
                    if (ChronoUnit.DAYS.between(last, now) >= 3) inactive3++;
                }
            } catch (Exception ignored) {}
        }

        // ── Top 5 users this week ─────────────────────────────────────
        List<Object[]> topRaw;
        try { topRaw = transactionRepository.topUsersByActivityInPeriod(weekStart, now); }
        catch (Exception e) { topRaw = java.util.Collections.emptyList(); }
        List<UserActivity> top5 = new ArrayList<>();
        for (int i = 0; i < Math.min(5, topRaw.size()); i++) {
            try {
                Object[] row = topRaw.get(i);
                if (row.length < 2 || row[0] == null) continue;
                User u   = (User) row[0];
                long cnt = toLong(row[1]);
                top5.add(new UserActivity(u.getDisplayName(), cnt));
            } catch (Exception ignored) {}
        }

        // ── Top categories this month (global) ───────────────────────
        List<Object[]> catRaw;
        try { catRaw = transactionRepository.topCategoriesGlobal(monthStart, now); }
        catch (Exception e) { catRaw = java.util.Collections.emptyList(); }
        List<CategoryTrend> topCats = new ArrayList<>();
        for (int i = 0; i < Math.min(5, catRaw.size()); i++) {
            try {
                Object[] row = catRaw.get(i);
                String name  = row.length > 0 && row[0] != null ? (String) row[0] : "?";
                String emoji = row.length > 1 && row[1] != null ? (String) row[1] : "";
                BigDecimal total = row.length > 2 ? toBD(row[2]) : BigDecimal.ZERO;
                long cnt         = row.length > 3 ? toLong(row[3]) : 0L;
                topCats.add(new CategoryTrend(
                        (!emoji.isEmpty() ? emoji + " " : "") + name, total, cnt));
            } catch (Exception ignored) {}
        }

        // ── Per-user retention table ──────────────────────────────────
        List<UserRetention> retention = new ArrayList<>();
        List<User> activeList = userRepository.findAllByStatus(UserStatus.ACTIVE);
        for (User u : activeList) {
            long txCount = 0;
            LocalDateTime lastTx = null;
            for (Object[] row : summary) {
                if (row.length > 0 && row[0] instanceof User ru && ru.getId().equals(u.getId())) {
                    txCount = row.length > 1 ? toLong(row[1]) : 0;
                    lastTx  = row.length > 2 && row[2] instanceof LocalDateTime lt ? lt : null;
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

    private static BigDecimal safe(java.util.function.Supplier<BigDecimal> fn) {
        try {
            BigDecimal v = fn.get();
            return v != null ? v : BigDecimal.ZERO;
        } catch (Exception e) { return BigDecimal.ZERO; }
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