package com.monetka.service;

import com.monetka.model.Subscription;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import com.monetka.service.StatisticsService.CategoryStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private static final ZoneId            BISHKEK  = ZoneId.of("Asia/Bishkek");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final StatisticsService     statisticsService;
    private final SubscriptionService   subscriptionService;
    private final FinancialTipsService  tipsService;
    private final TransactionRepository transactionRepository;
    private final PaydayService         paydayService;

    public ReportService(StatisticsService statisticsService,
                         SubscriptionService subscriptionService,
                         FinancialTipsService tipsService,
                         TransactionRepository transactionRepository,
                         PaydayService paydayService) {
        this.statisticsService  = statisticsService;
        this.subscriptionService = subscriptionService;
        this.tipsService         = tipsService;
        this.transactionRepository = transactionRepository;
        this.paydayService         = paydayService;
    }

    // ================================================================
    // Ежедневный отчёт 21:00 Bishkek
    // ================================================================

    @Transactional(readOnly = true)
    public String buildDailyReport(User user) {
        LocalDate now  = LocalDate.now(BISHKEK);
        LocalDateTime from = now.atStartOfDay();
        LocalDateTime to   = from.plusDays(1);

        BigDecimal expense = safe(transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to));
        BigDecimal income  = safe(transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.INCOME,  from, to));
        Map<String, BigDecimal> byCategory = statisticsService.getExpensesByCategory(user, from, to);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Итоги дня — ").append(now.format(DATE_FMT)).append("*\n\n");

        if (expense.compareTo(BigDecimal.ZERO) == 0 && income.compareTo(BigDecimal.ZERO) == 0) {
            sb.append("Сегодня транзакций не было 🌙\n\n");
            sb.append(tipsService.randomTip());
            return sb.toString();
        }

        if (income.compareTo(BigDecimal.ZERO) > 0)
            sb.append("💰 Доход: *+").append(fmt(income)).append("*\n");

        if (expense.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("💸 Расход: *−").append(fmt(expense)).append("*\n\n");
            if (!byCategory.isEmpty()) {
                sb.append("По категориям:\n");
                byCategory.forEach((cat, amt) ->
                        sb.append("  ").append(cat).append(" — ").append(fmt(amt)).append("\n"));
            }
        }

        sb.append("\n💳 Баланс: *").append(fmt(user.getBalance())).append("*");

        // Payday cycle — show tomorrow's budget
        paydayService.getCycleStatus(user).ifPresent(s -> {
            sb.append("\n\n────────────────\n");
            if (expense.compareTo(java.math.BigDecimal.ZERO) > 0) {
                int cmp = expense.compareTo(s.dailyBudget);
                if (cmp <= 0) {
                    sb.append("\u2705 \u0421\u0435\u0433\u043e\u0434\u043d\u044f \u0432 \u043f\u043b\u0430\u043d\u0435!\n");
                } else {
                    java.math.BigDecimal over = expense.subtract(s.dailyBudget);
                    sb.append("\u26A0\uFE0F \u041f\u0440\u0435\u0432\u044b\u0448\u0435\u043d\u0438\u0435: *+")
                            .append(fmt(over)).append("*\n");
                }
            }
            long tomorrowDays = Math.max(1, s.daysLeft);
            java.math.BigDecimal tomorrowBudget = s.remaining
                    .divide(java.math.BigDecimal.valueOf(tomorrowDays),
                            0, java.math.RoundingMode.HALF_UP);
            sb.append("\uD83D\uDCC5 \u041e\u0441\u0442\u0430\u043b\u043e\u0441\u044c \u0434\u043d\u0435\u0439: *")
                    .append(s.daysLeft).append("*\n");
            sb.append("\uD83D\uDCB5 \u041e\u0441\u0442\u0430\u0442\u043e\u043a: *")
                    .append(fmt(s.remaining)).append("*\n");
            sb.append("\uD83D\uDCCA \u0417\u0430\u0432\u0442\u0440\u0430 \u043c\u043e\u0436\u043d\u043e: *")
                    .append(fmt(tomorrowBudget)).append("/\u0434\u0435\u043d\u044c*");
        });

        sb.append("\n\n").append(tipsService.randomTip());
        return sb.toString();
    }

    // ================================================================
    // Статистика — сегодня
    // ================================================================

    @Transactional(readOnly = true)
    public String buildTodayStats(User user) {
        LocalDate now  = LocalDate.now(BISHKEK);
        LocalDateTime from = now.atStartOfDay();
        LocalDateTime to   = from.plusDays(1);
        return buildDetailedStats(user, from, to, "сегодня");
    }

    // ================================================================
    // Статистика — неделя
    // ================================================================

    @Transactional(readOnly = true)
    public String buildWeekStats(User user) {
        LocalDate now  = LocalDate.now(BISHKEK);
        LocalDateTime from = now.minusDays(6).atStartOfDay();
        LocalDateTime to   = now.plusDays(1).atStartOfDay();
        return buildDetailedStats(user, from, to, "последние 7 дней");
    }

    // ================================================================
    // Статистика — месяц (с подкатегориями + процентами)
    // ================================================================

    @Transactional(readOnly = true)
    public String buildMonthStats(User user) {
        LocalDate now  = LocalDate.now(BISHKEK);
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        BigDecimal income   = safe(statisticsService.getMonthIncome(user));
        BigDecimal expenses = safe(statisticsService.getMonthExpenses(user));
        BigDecimal diff     = income.subtract(expenses);
        String month        = statisticsService.currentMonthName();

        StringBuilder sb = new StringBuilder();
        sb.append("📈 *Статистика за ").append(month).append("*\n\n");
        sb.append("💰 Доходы: *+").append(fmt(income)).append("*\n");
        sb.append("💸 Расходы: *−").append(fmt(expenses)).append("*\n");
        sb.append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "✅" : "⚠️");
        sb.append(" Итого: *")
                .append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                .append(fmt(diff)).append("*\n");

        if (expenses.compareTo(BigDecimal.ZERO) > 0) {
            List<CategoryStats> cats = statisticsService.getDetailedExpenses(user, from, to);
            if (!cats.isEmpty()) {
                sb.append("\n*Расходы по категориям:*\n");
                for (CategoryStats cat : cats) {
                    sb.append("\n")
                            .append(cat.label)
                            .append(" — *").append(fmt(cat.total)).append("*")
                            .append(" _(").append(cat.percent).append("%)_\n");

                    // Подкатегории с отступом
                    for (StatisticsService.SubcategoryAmount sub : cat.subcats) {
                        sb.append("   ├ ").append(sub.name)
                                .append(" — ").append(fmt(sub.amount)).append("\n");
                    }
                }
                sb.append("\n*Всего расходов: ").append(fmt(expenses)).append("*");
            }
        }

        return sb.toString();
    }

    // ================================================================
    // Список подписок
    // ================================================================

    @Transactional(readOnly = true)
    public String buildSubscriptionsList(User user) {
        List<Subscription> subs = subscriptionService.getActiveSubscriptions(user);

        if (subs.isEmpty())
            return "У тебя нет активных подписок 📭\n\nДобавь первую — нажми кнопку ниже 👇";

        StringBuilder sb = new StringBuilder("🔄 *Твои подписки:*\n\n");
        BigDecimal totalMonth = BigDecimal.ZERO;
        BigDecimal totalYear  = BigDecimal.ZERO;

        for (Subscription sub : subs) {
            sb.append("▸ *").append(sub.getName()).append("*\n");
            sb.append("  💸 ").append(fmt(sub.getAmount())).append("/мес\n");
            sb.append("  📅 С ").append(sub.getStartDate().format(DATE_FMT));

            if (sub.getEndDate() != null) {
                sb.append(" до ").append(sub.getEndDate().format(DATE_FMT));
                Long days = sub.daysUntilExpiry();
                if (days != null) {
                    if (days == 0)      sb.append(" ⚠️ *истекает сегодня!*");
                    else if (days <= 3) sb.append(" ⚠️ *осталось ").append(days).append(" дн.*");
                    else if (days <= 7) sb.append(" ⏰ осталось ").append(days).append(" дн.");
                    else                sb.append(" ✅ ещё ").append(days).append(" дн.");
                } else {
                    sb.append(" 🔴 *истекла*");
                }
            } else {
                sb.append(" — бессрочная ♾");
            }
            sb.append("\n\n");
            totalMonth = totalMonth.add(sub.getAmount());
            totalYear  = totalYear.add(sub.getAmount().multiply(BigDecimal.valueOf(12)));
        }

        sb.append("─────────────────\n");
        sb.append("📊 В месяц: *").append(fmt(totalMonth)).append("*\n");
        sb.append("📊 В год: *").append(fmt(totalYear)).append("*\n");
        sb.append(whatCanYouBuyForYear(totalYear));
        return sb.toString();
    }

    // ================================================================
    // Helpers
    // ================================================================

    /**
     * Detailed stats for today / week — with subcategories and percentages.
     */
    @Transactional(readOnly = true)
    public String buildRangeStats(User user, LocalDateTime from, LocalDateTime to, String label) {
        return buildDetailedStats(user, from, to, label);
    }

    private String buildDetailedStats(User user, LocalDateTime from,
                                      LocalDateTime to, String label) {
        BigDecimal expense = safe(transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to));
        BigDecimal income  = safe(transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.INCOME,  from, to));

        StringBuilder sb = new StringBuilder("📊 *Статистика за ").append(label).append("*\n\n");

        if (expense.compareTo(BigDecimal.ZERO) == 0 && income.compareTo(BigDecimal.ZERO) == 0) {
            sb.append("Транзакций нет 🌙");
            return sb.toString();
        }

        if (income.compareTo(BigDecimal.ZERO) > 0)
            sb.append("💰 Доходы: *+").append(fmt(income)).append("*\n");
        if (expense.compareTo(BigDecimal.ZERO) > 0)
            sb.append("💸 Расходы: *−").append(fmt(expense)).append("*\n");

        if (expense.compareTo(BigDecimal.ZERO) > 0) {
            List<CategoryStats> cats = statisticsService.getDetailedExpenses(user, from, to);
            if (!cats.isEmpty()) {
                sb.append("\n*По категориям:*\n");
                for (CategoryStats cat : cats) {
                    sb.append("\n").append(cat.label)
                            .append(" — *").append(fmt(cat.total)).append("*")
                            .append(" _(").append(cat.percent).append("%)_\n");
                    for (StatisticsService.SubcategoryAmount sub : cat.subcats) {
                        sb.append("   ├ ").append(sub.name)
                                .append(" — ").append(fmt(sub.amount)).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private String whatCanYouBuyForYear(BigDecimal amount) {
        long sum = amount.longValue();
        if (sum < 5_000)   return "\n💡 Немного — хорошая мотивация оптимизировать подписки!";
        if (sum < 20_000)  return "\n💡 На эти деньги можно купить хорошие наушники или обновить гаджет.";
        if (sum < 50_000)  return "\n💡 Это смартфон среднего класса — подумай, нужны ли все подписки?";
        if (sum < 100_000) return "\n💡 Это ноутбук! Возможно, стоит пересмотреть список подписок.";
        return "\n⚠️ Серьёзная сумма в год — рекомендуем пересмотреть подписки!";
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private String fmt(BigDecimal amount) { return String.format("%,.0f сом", amount); }
}