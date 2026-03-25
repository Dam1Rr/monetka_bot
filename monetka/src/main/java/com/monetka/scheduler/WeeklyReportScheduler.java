package com.monetka.scheduler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import com.monetka.service.StatisticsService;
import com.monetka.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sends weekly summary every Monday at 10:00 Bishkek time.
 */
@Component
public class WeeklyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportScheduler.class);
    private static final ZoneId BISHKEK = com.monetka.util.AppConstants.BISHKEK;

    private final UserService           userService;
    private final StatisticsService     statisticsService;
    private final TransactionRepository transactionRepository;
    private final MonetkaBot            bot;

    public WeeklyReportScheduler(UserService userService,
                                 StatisticsService statisticsService,
                                 TransactionRepository transactionRepository,
                                 MonetkaBot bot) {
        this.userService           = userService;
        this.statisticsService     = statisticsService;
        this.transactionRepository = transactionRepository;
        this.bot                   = bot;
    }

    // Every Monday at 10:00 Bishkek
    @Async("broadcastExecutor")
    @Scheduled(cron = "0 0 10 * * MON", zone = "Asia/Bishkek")
    public void sendWeeklyReports() {
        List<User> users = userService.getActiveUsers();
        log.info("Sending weekly reports to {} users", users.size());

        LocalDate today      = LocalDate.now(BISHKEK);
        LocalDate weekStart  = today.minusDays(7);   // last Monday
        LocalDateTime from   = weekStart.atStartOfDay();
        LocalDateTime to     = today.atStartOfDay(); // up to today (exclusive)

        // Format: "3–9 марта"
        String weekLabel = weekStart.getDayOfMonth() + "–" + today.minusDays(1).getDayOfMonth() + " " +
                today.minusDays(1).getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));

        for (User user : users) {
            try {
                sendWeeklyReport(user, from, to, weekLabel);
            } catch (Exception e) {
                log.error("Failed weekly report for {}: {}", user.getTelegramId(), e.getMessage());
            }
        }
    }

    private void sendWeeklyReport(User user, LocalDateTime from, LocalDateTime to, String weekLabel) {
        BigDecimal expenses = safe(transactionRepository.sumByUserAndTypeAndPeriod(
                user, TransactionType.EXPENSE, from, to));
        BigDecimal income   = safe(transactionRepository.sumByUserAndTypeAndPeriod(
                user, TransactionType.INCOME, from, to));

        // Skip if no activity at all
        if (expenses.compareTo(BigDecimal.ZERO) == 0 && income.compareTo(BigDecimal.ZERO) == 0)
            return;

        // Category breakdown
        List<Object[]> cats = transactionRepository.sumExpensesByCategoryAndPeriod(user, from, to);

        // Compare with previous week
        LocalDateTime prevFrom = from.minusDays(7);
        LocalDateTime prevTo   = to.minusDays(7);
        BigDecimal prevExpenses = safe(transactionRepository.sumByUserAndTypeAndPeriod(
                user, TransactionType.EXPENSE, prevFrom, prevTo));

        // Find busiest day
        String busiestDay = findBusiestDay(user, from, to);

        StringBuilder sb = new StringBuilder();
        sb.append("📅 *Итоги недели — ").append(weekLabel).append("*\n\n");

        sb.append("💸 Потратил:  *−").append(fmt(expenses)).append("*\n");
        if (income.compareTo(BigDecimal.ZERO) > 0)
            sb.append("💰 Получил:   *+").append(fmt(income)).append("*\n");

        // Week-over-week comparison
        if (prevExpenses.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = expenses.subtract(prevExpenses);
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                sb.append("📈 На *").append(fmt(diff)).append("* больше прошлой недели\n");
            } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                sb.append("📉 На *").append(fmt(diff.abs())).append("* меньше прошлой недели 🎉\n");
            } else {
                sb.append("↔️ Столько же, что и на прошлой неделе\n");
            }
        }

        // Top categories
        if (!cats.isEmpty()) {
            sb.append("\n*Топ расходов:*\n");
            int show = Math.min(cats.size(), 4);
            for (int i = 0; i < show; i++) {
                Object[] row = cats.get(i);
                String  name  = (String)     row[0];
                String  emoji = (String)     row[1];
                BigDecimal amt = (BigDecimal) row[2];
                int pct = expenses.compareTo(BigDecimal.ZERO) > 0
                        ? amt.multiply(BigDecimal.valueOf(100))
                        .divide(expenses, 0, RoundingMode.HALF_UP).intValue()
                        : 0;
                sb.append(i + 1).append(". ");
                if (emoji != null) sb.append(emoji).append(" ");
                sb.append(name).append("  *").append(fmt(amt)).append("*");
                sb.append("  _(").append(pct).append("%)_\n");
            }
        }

        if (busiestDay != null)
            sb.append("\n💡 Самый дорогой день — *").append(busiestDay).append("*\n");

        sb.append("\nНовая неделя — новый старт 💪");

        bot.sendMessage(user.getTelegramId(), sb.toString(), KeyboardFactory.mainMenu());
    }

    /** Single DB query instead of 7 separate day-by-day queries. */
    private String findBusiestDay(User user, LocalDateTime from, LocalDateTime to) {
        List<com.monetka.model.Transaction> txs =
                transactionRepository.findByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);
        if (txs.isEmpty()) return null;

        Map<LocalDate, BigDecimal> byDay = new HashMap<>();
        for (com.monetka.model.Transaction tx : txs) {
            byDay.merge(tx.getCreatedAt().toLocalDate(), tx.getAmount(), BigDecimal::add);
        }
        return byDay.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().getDayOfWeek()
                        .getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru")))
                .orElse(null);
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private String fmt(BigDecimal v)      { return com.monetka.util.AppConstants.fmt(v); }
}