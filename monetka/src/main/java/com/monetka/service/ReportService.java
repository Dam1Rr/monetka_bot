package com.monetka.service;

import com.monetka.model.Subscription;
import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import com.monetka.service.StatisticsService;
import com.monetka.service.StatisticsService.CategoryStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.math.RoundingMode;
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
        LocalDate now = LocalDate.now(BISHKEK);
        LocalDateTime from = now.atStartOfDay();
        LocalDateTime to   = from.plusDays(1);

        BigDecimal expense = safe(transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to));
        BigDecimal income  = safe(transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.INCOME,  from, to));

        // Yesterday for comparison
        LocalDateTime yFrom = now.minusDays(1).atStartOfDay();
        LocalDateTime yTo   = now.atStartOfDay();
        BigDecimal yesterday = safe(transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, yFrom, yTo));

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCC5 *Сегодня — ").append(now.format(DateTimeFormatter.ofPattern("d MMMM", new java.util.Locale("ru")))).append("*\n\n");

        if (expense.compareTo(BigDecimal.ZERO) == 0 && income.compareTo(BigDecimal.ZERO) == 0) {
            sb.append("Транзакций пока нет 🌙");
            return sb.toString();
        }
        if (income.compareTo(BigDecimal.ZERO) > 0)
            sb.append("💰 Доходы: *+").append(fmt(income)).append("*\n");
        if (expense.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("💸 Потрачено: *−").append(fmt(expense)).append("*\n");
            if (yesterday.compareTo(BigDecimal.ZERO) > 0) {
                int diff = expense.compareTo(yesterday);
                String cmp = diff > 0 ? "больше чем вчера (" + fmt(yesterday) + ")" : diff < 0 ? "меньше чем вчера (" + fmt(yesterday) + ")" : "как вчера";
                sb.append("📊 ").append(cmp).append("\n");
            }
        }

        // Per-category breakdown
        List<CategoryStats> cats = statisticsService.getDetailedExpenses(user, from, to);
        if (!cats.isEmpty()) {
            sb.append("\n*По категориям:*\n");
            for (CategoryStats cat : cats) {
                sb.append("\n").append(cat.label)
                        .append("  *").append(fmt(cat.total)).append("*")
                        .append("  _(").append(cat.percent).append("%)_\n");
                for (StatisticsService.SubcategoryAmount sub : cat.subcats) {
                    sb.append("   ├ ").append(sub.name).append(" — ").append(fmt(sub.amount)).append("\n");
                }
            }
        }

        // Each transaction
        List<Transaction> txs = transactionRepository.findByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);
        if (!txs.isEmpty()) {
            sb.append("\n*Каждая трата:*\n");
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
            for (Transaction tx : txs) {
                String catLabel = tx.getCategory() != null ? tx.getCategory().getDisplayName() : "—";
                sb.append("▸ ").append(tx.getDescription())
                        .append("  −").append(fmt(tx.getAmount()))
                        .append("  · ").append(tx.getCreatedAt().atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(BISHKEK).format(timeFmt)).append("\n");
            }
        }
        return sb.toString();
    }

    // ================================================================
    // Статистика — неделя
    // ================================================================

    @Transactional(readOnly = true)
    public String buildWeekStats(User user) {
        LocalDate now  = LocalDate.now(BISHKEK);
        LocalDateTime from = now.minusDays(6).atStartOfDay();
        LocalDateTime to   = now.plusDays(1).atStartOfDay();

        BigDecimal total = safe(transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to));
        List<CategoryStats> cats = statisticsService.getDetailedExpenses(user, from, to);

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCC6 *Неделя — ")
                .append(now.minusDays(6).format(DateTimeFormatter.ofPattern("d MMM", new java.util.Locale("ru"))))
                .append("–")
                .append(now.format(DateTimeFormatter.ofPattern("d MMM", new java.util.Locale("ru"))))
                .append("*\n\n");

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            sb.append("Расходов за неделю нет 🌱");
            return sb.toString();
        }

        long days = 7;
        BigDecimal avg = total.divide(java.math.BigDecimal.valueOf(days), 0, java.math.RoundingMode.HALF_UP);
        sb.append("💸 Потрачено: *−").append(fmt(total)).append("*\n");
        sb.append("📊 В среднем: *").append(fmt(avg)).append("/день*\n");

        if (!cats.isEmpty()) {
            sb.append("\n*Топ категорий:*\n");
            int show = Math.min(cats.size(), 5);
            for (int i = 0; i < show; i++) {
                CategoryStats c = cats.get(i);
                sb.append("\n").append(i + 1).append(". ").append(c.label)
                        .append("  *").append(fmt(c.total)).append("*")
                        .append("  _(").append(c.percent).append("%)_\n");
                for (StatisticsService.SubcategoryAmount sub : c.subcats) {
                    sb.append("   ├ ").append(sub.name).append(" — ").append(fmt(sub.amount)).append("\n");
                }
            }
        }

        // Day-by-day activity bar
        sb.append("\n*Активность по дням:*\n");
        String[] dayNames = {"Пн","Вт","Ср","Чт","Пт","Сб","Вс"};
        BigDecimal maxDay = BigDecimal.ONE;
        List<BigDecimal> dayTotals = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = now.minusDays(i);
            BigDecimal dayAmt = safe(transactionRepository.sumByUserAndTypeAndPeriod(
                    user, TransactionType.EXPENSE, d.atStartOfDay(), d.plusDays(1).atStartOfDay()));
            dayTotals.add(dayAmt);
            if (dayAmt.compareTo(maxDay) > 0) maxDay = dayAmt;
        }
        for (int i = 0; i < 7; i++) {
            LocalDate d = now.minusDays(6 - i);
            BigDecimal amt = dayTotals.get(i);
            int barLen = maxDay.compareTo(BigDecimal.ZERO) > 0
                    ? amt.multiply(java.math.BigDecimal.valueOf(8)).divide(maxDay, 0, java.math.RoundingMode.HALF_UP).intValue()
                    : 0;
            String bar = "█".repeat(barLen) + "░".repeat(8 - barLen);
            String dn = dayNames[d.getDayOfWeek().getValue() - 1];
            sb.append(dn).append("  ").append(bar).append("  ")
                    .append(amt.compareTo(BigDecimal.ZERO) > 0 ? fmt(amt) : "—").append("\n");
        }
        return sb.toString();
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

        // Payday/pace data
        int dayOfMonth   = now.getDayOfMonth();
        int daysInMonth  = now.lengthOfMonth();
        int daysLeft     = daysInMonth - dayOfMonth;

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCC8 *").append(month).append("*\n");
        sb.append("_Прошло ").append(dayOfMonth).append(" дн. · осталось ").append(daysLeft).append(" дн._\n\n");

        if (income.compareTo(BigDecimal.ZERO) > 0)
            sb.append("💰 Доходы:  *+").append(fmt(income)).append("*\n");
        sb.append("💸 Расходы: *−").append(fmt(expenses)).append("*\n");

        if (income.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "✅" : "⚠️");
            sb.append(" Баланс: *")
                    .append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                    .append(fmt(diff)).append("*\n");
        }

        // Categories
        List<CategoryStats> cats = statisticsService.getDetailedExpenses(user, from, to);
        if (!cats.isEmpty() && expenses.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("\n*Расходы по категориям:*\n");
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

        // ── AI финансовый разбор ──
        if (expenses.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("\n").append("━".repeat(16)).append("\n");
            sb.append("\uD83E\uDDE0 *AI разбор месяца*\n\n");

            // 1. Темп трат + прогноз
            if (dayOfMonth > 0) {
                BigDecimal dailyRate = expenses.divide(
                        java.math.BigDecimal.valueOf(dayOfMonth), 0, RoundingMode.HALF_UP);
                BigDecimal projected = dailyRate.multiply(java.math.BigDecimal.valueOf(daysInMonth));
                sb.append("📊 Темп: *").append(fmt(dailyRate)).append("/день*\n");
                sb.append("\uD83D\uDD2E Прогноз к концу месяца: *~").append(fmt(projected)).append("*\n");

                if (income.compareTo(BigDecimal.ZERO) > 0) {
                    if (projected.compareTo(income) > 0) {
                        BigDecimal over = projected.subtract(income);
                        sb.append("\u26A0\uFE0F _При таком темпе минус ~").append(fmt(over)).append(" — пора тормозить._\n");
                    } else {
                        BigDecimal saveProj = income.subtract(projected);
                        int saveProjPct = saveProj.multiply(java.math.BigDecimal.valueOf(100))
                                .divide(income, 0, RoundingMode.HALF_UP).intValue();
                        sb.append("\u2705 _Прогноз: останется ~").append(fmt(saveProj))
                                .append(" (").append(saveProjPct).append("% дохода)_\n");
                    }
                }
            }

            // 2. Разбор категорий с советами
            if (!cats.isEmpty()) {
                sb.append("\n*Разбор по категориям:*\n");
                for (int i = 0; i < Math.min(cats.size(), 4); i++) {
                    CategoryStats cat = cats.get(i);
                    String advice = "";
                    String catName = cat.label;
                    if (cat.percent > 40) {
                        if (catName.contains("Еда") || catName.contains("Кафе") || catName.contains("Доставка") || catName.contains("Фастфуд"))
                            advice = " _→ готовь дома 3+ дней в неделю_";
                        else if (catName.contains("Такси") || catName.contains("Транспорт"))
                            advice = " _→ попробуй проездной_";
                        else if (catName.contains("Развлечения") || catName.contains("Подписки"))
                            advice = " _→ проверь, всем ли пользуешься_";
                        else
                            advice = " _→ главная статья месяца_";
                    } else if (cat.percent > 25) {
                        advice = " _→ в норме_";
                    }
                    sb.append("\n").append(catName)
                            .append(" — *").append(fmt(cat.total)).append("*")
                            .append(" (").append(cat.percent).append("%)")
                            .append(advice).append("\n");
                    if (!cat.subcats.isEmpty()) {
                        StatisticsService.SubcategoryAmount topSub = cat.subcats.get(0);
                        sb.append("   \u21B3 больше всего: ").append(topSub.name)
                                .append(" — ").append(fmt(topSub.amount)).append("\n");
                    }
                }
            }

            // 3. Норма сбережений
            if (income.compareTo(BigDecimal.ZERO) > 0) {
                int savePct = diff.multiply(java.math.BigDecimal.valueOf(100))
                        .divide(income, 0, RoundingMode.HALF_UP).intValue();
                sb.append("\n*Сбережения:* ");
                if (savePct >= 30)
                    sb.append("*").append(savePct).append("%* \uD83C\uDFC6 _Отлично!_\n");
                else if (savePct >= 20)
                    sb.append("*").append(savePct).append("%* \u2705 _Хорошо, цель достигнута_\n");
                else if (savePct >= 10)
                    sb.append("*").append(savePct).append("%* \uD83D\uDCC8 _Норма, цель 20%+_\n");
                else if (savePct > 0)
                    sb.append("*").append(savePct).append("%* \u26A1 _Мало — откладывай 10% с каждого дохода_\n");
                else
                    sb.append("*0%* \u26A0\uFE0F _Расходы ≥ доходов — пора пересмотреть бюджет_\n");

                if (diff.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal yearSavings = diff.multiply(java.math.BigDecimal.valueOf(12));
                    sb.append("\uD83D\uDCA1 _При таком темпе за год отложишь *").append(fmt(yearSavings)).append("*_\n");
                }
            }

            // 4. Главный совет
            sb.append("\n");
            if (!cats.isEmpty() && cats.get(0).percent > 50) {
                BigDecimal saving20 = cats.get(0).total
                        .multiply(java.math.BigDecimal.valueOf(20))
                        .divide(java.math.BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                sb.append("\uD83D\uDCCC *Совет:* сократи *").append(cats.get(0).label)
                        .append("* на 20% — сэкономишь ~*").append(fmt(saving20)).append("* в месяц\n");
            } else if (cats.size() >= 2) {
                sb.append("\uD83D\uDCAC _Поставь лимиты на топ-категории — бот предупредит когда пора остановиться._\n");
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