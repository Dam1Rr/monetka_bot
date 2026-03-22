package com.monetka.controller;

import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.model.enums.UserStatus;
import com.monetka.repository.TransactionRepository;
import com.monetka.repository.UserRepository;
import com.monetka.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API для admin дашборда.
 * Защищён API-ключом в заголовке X-Admin-Key.
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "${admin.cors-origin:*}")
public class AdminDashboardController {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    @Value("${admin.api-key:monetka-secret-2026}")
    private String adminApiKey;

    private final UserRepository        userRepository;
    private final TransactionRepository transactionRepository;

    public AdminDashboardController(UserRepository userRepository,
                                    TransactionRepository transactionRepository) {
        this.userRepository        = userRepository;
        this.transactionRepository = transactionRepository;
    }

    // ── Auth check ─────────────────────────────────────────────────

    private boolean isAuthorized(String key) {
        return adminApiKey.equals(key);
    }

    // ── /api/admin/stats — главные метрики ─────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestHeader("X-Admin-Key") String key) {
        if (!isAuthorized(key)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        LocalDate today     = LocalDate.now(BISHKEK);
        LocalDateTime from7 = today.minusDays(7).atStartOfDay();
        LocalDateTime from30= today.minusDays(30).atStartOfDay();
        LocalDateTime now   = LocalDateTime.now(BISHKEK);

        long totalUsers    = userRepository.countByStatus(UserStatus.ACTIVE);
        long blockedUsers  = userRepository.countByBlockedBotTrue();
        long newUsersWeek  = userRepository.countByCreatedAtAfter(from7);

        long txTotal       = transactionRepository.count();
        long txToday       = transactionRepository.countByCreatedAtAfter(today.atStartOfDay());
        long txWeek        = transactionRepository.countByCreatedAtAfter(from7);
        long txMonth       = transactionRepository.countByCreatedAtAfter(from30);

        BigDecimal totalTracked = transactionRepository.sumAllExpenses();
        if (totalTracked == null) totalTracked = BigDecimal.ZERO;

        // DAU / WAU
        long dau = transactionRepository.countDistinctUsersByPeriod(today.atStartOfDay(), now);
        long wau = transactionRepository.countDistinctUsersByPeriod(from7, now);

        // Retention (юзеры у которых были транзакции за последние 7 дней)
        long activeWeek = transactionRepository.countDistinctUsersByPeriod(from7, now);
        int retention   = totalUsers > 0 ? (int)(activeWeek * 100 / totalUsers) : 0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("users",        Map.of("total", totalUsers, "blocked", blockedUsers, "newThisWeek", newUsersWeek));
        stats.put("transactions", Map.of("total", txTotal, "today", txToday, "week", txWeek, "month", txMonth));
        stats.put("engagement",   Map.of("dau", dau, "wau", wau, "retention7d", retention));
        stats.put("financial",    Map.of("totalTrackedSom", totalTracked));
        stats.put("updatedAt",    LocalDateTime.now(BISHKEK).toString());

        return ResponseEntity.ok(stats);
    }

    // ── /api/admin/users — список пользователей ────────────────────

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(@RequestHeader("X-Admin-Key") String key) {
        if (!isAuthorized(key)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        LocalDateTime from7  = LocalDate.now(BISHKEK).minusDays(7).atStartOfDay();
        LocalDateTime from30 = LocalDate.now(BISHKEK).minusDays(30).atStartOfDay();
        LocalDateTime now    = LocalDateTime.now(BISHKEK);

        List<User> users = userRepository.findAllByStatusOrderByCreatedAtDesc(UserStatus.ACTIVE);

        List<Map<String, Object>> result = users.stream().map(u -> {
            long txWeek  = transactionRepository.countByUserAndCreatedAtAfter(u, from7);
            long txMonth = transactionRepository.countByUserAndCreatedAtAfter(u, from30);
            BigDecimal spent = transactionRepository.sumByUserAndTypeAndPeriod(
                    u, TransactionType.EXPENSE, from30, now);

            String activity;
            if (txWeek >= 5)       activity = "active";
            else if (txWeek >= 1)  activity = "low";
            else                   activity = "inactive";

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",           u.getId());
            m.put("telegramId",   u.getTelegramId());
            m.put("name",         u.getDisplayName());
            m.put("username",     u.getUsername());
            m.put("streak",       u.getStreakDays());
            m.put("maxStreak",    u.getMaxStreakDays());
            m.put("txWeek",       txWeek);
            m.put("txMonth",      txMonth);
            m.put("spentMonth",   spent != null ? spent : BigDecimal.ZERO);
            m.put("balance",      u.getBalance());
            m.put("activity",     activity);
            m.put("blocked",      u.isBlockedBot());
            m.put("joinedAt",     u.getCreatedAt() != null ? u.getCreatedAt().toLocalDate().toString() : null);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── /api/admin/transactions — последние транзакции ─────────────

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(
            @RequestHeader("X-Admin-Key") String key,
            @RequestParam(defaultValue = "50") int limit) {
        if (!isAuthorized(key)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        var txs = transactionRepository.findTopByOrderByCreatedAtDesc(limit);

        List<Map<String, Object>> result = txs.stream().map(tx -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",          tx.getId());
            m.put("user",        tx.getUser().getDisplayName());
            m.put("description", tx.getDescription());
            m.put("amount",      tx.getAmount());
            m.put("type",        tx.getType().name());
            m.put("category",    tx.getCategory() != null ? tx.getCategory().getName() : "—");
            m.put("subcategory", tx.getSubcategory() != null ? tx.getSubcategory().getName() : null);
            m.put("createdAt",   tx.getCreatedAt().toString());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── /api/admin/activity — активность по дням ───────────────────

    @GetMapping("/activity")
    public ResponseEntity<?> getActivity(@RequestHeader("X-Admin-Key") String key) {
        if (!isAuthorized(key)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        LocalDate today = LocalDate.now(BISHKEK);
        List<Map<String, Object>> result = new ArrayList<>();

        for (int i = 29; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime from = day.atStartOfDay();
            LocalDateTime to   = day.plusDays(1).atStartOfDay();
            long count = transactionRepository.countByCreatedAtBetween(from, to);
            long users = transactionRepository.countDistinctUsersByPeriod(from, to);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date",  day.toString());
            m.put("txCount", count);
            m.put("activeUsers", users);
            result.add(m);
        }

        return ResponseEntity.ok(result);
    }

    // ── /api/admin/categories — топ категорий ──────────────────────

    @GetMapping("/categories")
    public ResponseEntity<?> getCategories(@RequestHeader("X-Admin-Key") String key) {
        if (!isAuthorized(key)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        LocalDateTime from = LocalDate.now(BISHKEK).withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        var cats = transactionRepository.sumExpensesByCategoryAllUsers(from, to);

        List<Map<String, Object>> result = cats.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name",   row[0]);
            m.put("emoji",  row[1]);
            m.put("total",  row[2]);
            m.put("count",  row[3]);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── /api/admin/public — публичные метрики для сайта ────────────

    @GetMapping("/public")
    public ResponseEntity<?> getPublicStats() {
        // Этот endpoint БЕЗ авторизации — для отображения на сайте
        long users    = userRepository.countByStatus(UserStatus.ACTIVE);
        long txCount  = transactionRepository.count();
        BigDecimal tracked = transactionRepository.sumAllExpenses();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("users",       users);
        stats.put("transactions", txCount);
        stats.put("trackedSom",  tracked != null ? tracked.longValue() : 0);
        return ResponseEntity.ok(stats);
    }

    // ── /api/admin/broadcast ────────────────────────────────────────

    @org.springframework.beans.factory.annotation.Autowired
    private com.monetka.bot.MonetkaBot bot;

    @PostMapping("/broadcast")
    public ResponseEntity<?> sendBroadcast(
            @RequestHeader("X-Admin-Key") String key,
            @RequestBody Map<String, String> body) {
        if (!isAuthorized(key)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        String text = body.get("text");
        if (text == null || text.isBlank()) return ResponseEntity.badRequest().body("Empty text");

        List<com.monetka.model.User> users = userRepository.findAllByStatusAndBlockedBotFalse(UserStatus.ACTIVE);
        int sent = 0, failed = 0;
        for (com.monetka.model.User u : users) {
            try {
                bot.sendMessage(u.getTelegramId(), "📢 Сообщение от Monetka\n\n" + text,
                        com.monetka.bot.keyboard.KeyboardFactory.mainMenu());
                sent++;
                Thread.sleep(50);
            } catch (Exception e) { failed++; }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", sent); result.put("failed", failed); result.put("total", users.size());
        return ResponseEntity.ok(result);
    }
}