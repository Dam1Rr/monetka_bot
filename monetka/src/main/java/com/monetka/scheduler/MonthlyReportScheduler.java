package com.monetka.scheduler;

import com.monetka.bot.MonetkaBot;
import com.monetka.insight.MonthlyPortrait;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.BudgetGoal;
import com.monetka.model.User;
import com.monetka.service.BudgetService;
import com.monetka.service.StatisticsService;
import com.monetka.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Sends monthly financial recap on the 1st of each month at 10:00 Bishkek.
 */
@Component
public class MonthlyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportScheduler.class);
    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private final UserService       userService;
    private final StatisticsService statisticsService;
    private final BudgetService     budgetService;
    private final MonthlyPortrait   monthlyPortrait;
    private final MonetkaBot        bot;

    public MonthlyReportScheduler(UserService userService,
                                  StatisticsService statisticsService,
                                  BudgetService budgetService,
                                  MonthlyPortrait monthlyPortrait,
                                  MonetkaBot bot) {
        this.userService       = userService;
        this.statisticsService = statisticsService;
        this.budgetService     = budgetService;
        this.monthlyPortrait   = monthlyPortrait;
        this.bot               = bot;
    }

    // 1st of month, 10:00 Bishkek
    @Scheduled(cron = "0 0 10 1 * *", zone = "Asia/Bishkek")
    @Transactional(readOnly = true)
    public void sendMonthlyReports() {
        List<User> users = userService.getActiveUsers();
        log.info("Sending monthly reports to {} users", users.size());

        LocalDate prevMonthDate = LocalDate.now(BISHKEK).minusMonths(1);
        LocalDateTime from = prevMonthDate.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);
        String monthName = prevMonthDate.getMonth()
                .getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));

        for (User user : users) {
            try {
                sendMonthlyReport(user, from, to, monthName);
            } catch (Exception e) {
                log.error("Failed monthly report for {}: {}", user.getTelegramId(), e.getMessage());
            }
        }
    }

    private void sendMonthlyReport(User user, LocalDateTime from, LocalDateTime to, String monthName) {
        BigDecimal income   = safe(statisticsService.getMonthIncomeForPeriod(user, from, to));
        BigDecimal expenses = safe(statisticsService.getMonthExpensesForPeriod(user, from, to));
        BigDecimal saved    = income.subtract(expenses);

        if (expenses.compareTo(BigDecimal.ZERO) == 0 && income.compareTo(BigDecimal.ZERO) == 0)
            return; // no activity — skip

        List<StatisticsService.CategoryStats> cats =
                statisticsService.getDetailedExpenses(user, from, to);
        List<BudgetGoal> goals = budgetService.getGoals(user);

        StringBuilder sb = new StringBuilder();
        sb.append("🎊 *").append(monthName).append(" — итоги*\n\n");

        if (income.compareTo(BigDecimal.ZERO) > 0)
            sb.append("💰 Заработал:  *+").append(fmt(income)).append("*\n");
        sb.append("💸 Потратил:   *−").append(fmt(expenses)).append("*\n");

        if (saved.compareTo(BigDecimal.ZERO) >= 0) {
            int savePct = income.compareTo(BigDecimal.ZERO) > 0
                    ? saved.multiply(BigDecimal.valueOf(100))
                    .divide(income, 0, RoundingMode.HALF_UP).intValue()
                    : 0;
            sb.append("💚 Сберёг:     *+").append(fmt(saved)).append("*");
            if (savePct > 0) sb.append("  _(").append(savePct).append("%)_");
            sb.append("\n");
        } else {
            sb.append("⚠️ Перерасход: *").append(fmt(saved.abs())).append("*\n");
        }

        // Best category vs goal
        if (!goals.isEmpty() && !cats.isEmpty()) {
            sb.append("\n");
            String best = null; String worst = null;
            for (StatisticsService.CategoryStats cat : cats) {
                goals.stream()
                        .filter(g -> cat.label.contains(g.getCategory().getName()))
                        .findFirst()
                        .ifPresent(g -> {
                            // just append inline — closures can't modify local vars
                        });
            }
            // Find goal results
            for (BudgetGoal g : goals) {
                BigDecimal spent = BigDecimal.ZERO;
                for (StatisticsService.CategoryStats c : cats) {
                    if (c.label.contains(g.getCategory().getName())) {
                        spent = c.total; break;
                    }
                }
                if (spent.compareTo(g.getAmount()) <= 0) {
                    sb.append("🏆 *").append(g.getCategory().getName())
                            .append("* — уложился в цель ✅\n");
                    break;
                }
            }
            for (BudgetGoal g : goals) {
                BigDecimal spent = BigDecimal.ZERO;
                for (StatisticsService.CategoryStats c : cats) {
                    if (c.label.contains(g.getCategory().getName())) {
                        spent = c.total; break;
                    }
                }
                if (spent.compareTo(g.getAmount()) > 0) {
                    BigDecimal over = spent.subtract(g.getAmount());
                    sb.append("😅 *").append(g.getCategory().getName())
                            .append("* — превышение на ").append(fmt(over)).append("\n");
                    break;
                }
            }
        }

        // Top 3 categories
        if (!cats.isEmpty()) {
            sb.append("\n*Топ расходов:*\n");
            int show = Math.min(3, cats.size());
            for (int i = 0; i < show; i++) {
                sb.append(i + 1).append(". ").append(cats.get(i).label)
                        .append(" — *").append(fmt(cats.get(i).total)).append("*\n");
            }
        }

        sb.append("\nНовый месяц начался с чистого листа 🚀");

        bot.sendMessage(user.getTelegramId(), sb.toString(), KeyboardFactory.mainMenu());

        // Психологический портрет — сразу после отчёта
        String portrait = monthlyPortrait.build(user);
        if (portrait != null) {
            bot.sendMarkdown(user.getTelegramId(), portrait);
        }
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private String fmt(BigDecimal v) { return String.format("%,.0f сом", v); }
}