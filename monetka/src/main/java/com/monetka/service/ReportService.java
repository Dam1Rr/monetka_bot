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
    private final AiInsightService      aiInsightService;

    public ReportService(StatisticsService statisticsService,
                         SubscriptionService subscriptionService,
                         FinancialTipsService tipsService,
                         TransactionRepository transactionRepository,
                         PaydayService paydayService,
                         AiInsightService aiInsightService) {
        this.statisticsService     = statisticsService;
        this.subscriptionService   = subscriptionService;
        this.tipsService           = tipsService;
        this.transactionRepository = transactionRepository;
        this.paydayService         = paydayService;
        this.aiInsightService      = aiInsightService;
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
            sb.append("\uD83D\uDCCA \u0417\u0430\u0432\u0442\u0440\u0430 \u043c\u043e\u0436\u043d\u043e: *")
                    .append(fmt(tomorrowBudget)).append("/\u0434\u0435\u043d\u044c*");
        });

        sb.append("\n\n").append(tipsService.randomTip());
        sb.append("\n\n_Хорошего вечера! Завтра новый день 💪_");
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
                        .append("  · ").append(tx.getCreatedAt().format(timeFmt)).append("\n");
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
        LocalDateTime prevFrom = from.minusDays(7);

        BigDecimal total     = safe(transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to));
        BigDecimal prevTotal = safe(transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, prevFrom, from));
        List<CategoryStats> cats = statisticsService.getDetailedExpenses(user, from, to);
        List<Transaction> weekTxs = transactionRepository.findByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);

        // Peak day
        java.util.Map<LocalDate, BigDecimal> byDay = new java.util.LinkedHashMap<>();
        for (Transaction tx : weekTxs) {
            LocalDate d = tx.getCreatedAt().toLocalDate();
            byDay.merge(d, tx.getAmount(), BigDecimal::add);
        }
        LocalDate peakDay = null;
        BigDecimal peakAmt = BigDecimal.ZERO;
        for (java.util.Map.Entry<LocalDate, BigDecimal> e : byDay.entrySet()) {
            if (e.getValue().compareTo(peakAmt) > 0) { peakAmt = e.getValue(); peakDay = e.getKey(); }
        }

        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dmm = DateTimeFormatter.ofPattern("d MMM", new java.util.Locale("ru"));
        sb.append("\uD83D\uDCC6 *\u041d\u0435\u0434\u0435\u043b\u044f \u2014 ")
                .append(now.minusDays(6).format(dmm)).append("\u2013").append(now.format(dmm))
                .append("*\n\n");

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            sb.append("\u0420\u0430\u0441\u0445\u043e\u0434\u043e\u0432 \u0437\u0430 \u043d\u0435\u0434\u0435\u043b\u044e \u043d\u0435\u0442 \uD83C\uDF31");
            return sb.toString();
        }

        BigDecimal avg = total.divide(java.math.BigDecimal.valueOf(7), 0, RoundingMode.HALF_UP);
        sb.append("\uD83D\uDCB8 *").append(fmt(total)).append("*  \u2022  ").append(fmt(avg)).append("/\u0434\u0435\u043d\u044c\n");

        if (prevTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal delta = total.subtract(prevTotal);
            int pct = delta.abs().multiply(java.math.BigDecimal.valueOf(100))
                    .divide(prevTotal, 0, RoundingMode.HALF_UP).intValue();
            if (delta.compareTo(BigDecimal.ZERO) > 0)
                sb.append("_\u041d\u0430 ").append(pct).append("% \u0431\u043e\u043b\u044c\u0448\u0435 \u043f\u0440\u043e\u0448\u043b\u043e\u0439 \u043d\u0435\u0434\u0435\u043b\u0438_\n");
            else
                sb.append("_\u041d\u0430 ").append(pct).append("% \u043c\u0435\u043d\u044c\u0448\u0435 \u043f\u0440\u043e\u0448\u043b\u043e\u0439 \u043d\u0435\u0434\u0435\u043b\u0438 \u2705_\n");
        }

        if (!cats.isEmpty()) {
            sb.append("\n*\u041d\u0430 \u0447\u0442\u043e \u0443\u0448\u043b\u043e:*\n");
            int show = Math.min(cats.size(), 5);
            for (int i = 0; i < show; i++) {
                CategoryStats cat = cats.get(i);
                sb.append(cat.label).append("  *").append(fmt(cat.total)).append("*  _").append(cat.percent).append("%_\n");
                int subShow = Math.min(cat.subcats.size(), 3);
                for (int j = 0; j < subShow; j++) {
                    StatisticsService.SubcategoryAmount sub = cat.subcats.get(j);
                    String prefix = (j == subShow - 1) ? "   \u2514 " : "   \u251C ";
                    sb.append(prefix).append(sub.name).append(" \u2014 ").append(fmt(sub.amount)).append("\n");
                }
            }
        }

        sb.append("\n*\u041f\u043e \u0434\u043d\u044f\u043c:*\n");
        String[] dayNames = {"\u041f\u043d","\u0412\u0442","\u0421\u0440","\u0427\u0442","\u041f\u0442","\u0421\u0431","\u0412\u0441"};
        List<BigDecimal> dayTotals = new java.util.ArrayList<>();
        BigDecimal maxDay = BigDecimal.ONE;
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
                    ? amt.multiply(java.math.BigDecimal.valueOf(8)).divide(maxDay, 0, RoundingMode.HALF_UP).intValue() : 0;
            String bar = "\u2588".repeat(barLen) + "\u2591".repeat(8 - barLen);
            String dn = dayNames[d.getDayOfWeek().getValue() - 1];
            boolean isPeak = peakDay != null && d.equals(peakDay) && peakAmt.compareTo(BigDecimal.valueOf(500)) > 0;
            sb.append(dn).append("  ").append(bar).append("  ")
                    .append(amt.compareTo(BigDecimal.ZERO) > 0 ? fmt(amt) : "\u2014")
                    .append(isPeak ? " \uD83D\uDD34" : "").append("\n");
        }

        String aiInsight = aiInsightService.generateWeekInsight(
                cats, total, prevTotal, peakDay, peakAmt, avg, weekTxs);
        if (aiInsight != null && !aiInsight.isBlank()) {
            sb.append("\n\uD83E\uDDE0 ").append(aiInsight);
        }

        return sb.toString();
    }

    // ================================================================
    // Статистика — месяц (с подкатегориями + процентами)
    // ================================================================

    @Transactional(readOnly = true)
    public String buildMonthStats(User user) {
        LocalDate now       = LocalDate.now(BISHKEK);
        LocalDateTime from  = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to    = from.plusMonths(1);

        // Текущий месяц
        BigDecimal income   = safe(statisticsService.getMonthIncome(user));
        BigDecimal expenses = safe(statisticsService.getMonthExpenses(user));
        BigDecimal diff     = income.subtract(expenses);
        String monthName    = statisticsService.currentMonthName();

        int dayOfMonth  = now.getDayOfMonth();
        int daysInMonth = now.lengthOfMonth();
        int daysLeft    = daysInMonth - dayOfMonth;

        // Прошлый месяц
        LocalDateTime prevFrom = from.minusMonths(1);
        LocalDateTime prevTo   = from;
        BigDecimal prevExpenses = safe(statisticsService.getMonthExpensesForPeriod(user, prevFrom, prevTo));

        // Средний день и пиковый день
        List<Transaction> txs = transactionRepository.findByUserAndTypeAndPeriod(
                user, TransactionType.EXPENSE, from, to);

        BigDecimal dailyAvg = BigDecimal.ZERO;
        String peakDayStr   = null;
        BigDecimal peakAmt  = BigDecimal.ZERO;

        if (dayOfMonth > 0 && expenses.compareTo(BigDecimal.ZERO) > 0) {
            dailyAvg = expenses.divide(java.math.BigDecimal.valueOf(dayOfMonth), 0, RoundingMode.HALF_UP);
        }

        // Считаем максимальный день
        java.util.Map<java.time.LocalDate, BigDecimal> byDay = new java.util.HashMap<>();
        for (Transaction tx : txs) {
            java.time.LocalDate d = tx.getCreatedAt().toLocalDate();
            byDay.merge(d, tx.getAmount(), BigDecimal::add);
        }
        for (java.util.Map.Entry<java.time.LocalDate, BigDecimal> e : byDay.entrySet()) {
            if (e.getValue().compareTo(peakAmt) > 0) {
                peakAmt = e.getValue();
                peakDayStr = e.getKey().getDayOfMonth() + " " + monthName.toLowerCase();
            }
        }

        // Категории
        List<CategoryStats> cats = statisticsService.getDetailedExpenses(user, from, to);

        // ── Строим сообщение ──
        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDDD3 *").append(monthName).append(" ").append(now.getYear()).append("*\n");
        sb.append("_").append(dayOfMonth).append(" дней прошло · ").append(daysLeft).append(" осталось_\n\n");

        // Главные цифры
        if (income.compareTo(BigDecimal.ZERO) > 0)
            sb.append("\uD83D\uDCB0 Заработок:  *+").append(fmt(income)).append("*\n");
        sb.append("\uD83D\uDCB8 Расходы:    *\u2212").append(fmt(expenses)).append("*\n");
        if (income.compareTo(BigDecimal.ZERO) > 0) {
            String icon = diff.compareTo(BigDecimal.ZERO) >= 0 ? "\u2705" : "\u26A0\uFE0F";
            sb.append(icon).append(" Осталось:   *").append(fmt(diff)).append("*\n");
        }
        sb.append("\n");
        sb.append("\uD83D\uDCCA Каждый день тратишь в среднем *").append(fmt(dailyAvg)).append("*\n");
        if (peakDayStr != null)
            sb.append("\uD83D\uDCC5 Самый дорогой день — ").append(peakDayStr)
                    .append(" (*").append(fmt(peakAmt)).append("*) \uD83D\uDE2C\n");

        // Категории с подкатегориями — как в недельном
        if (!cats.isEmpty() && expenses.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("\n").append("\u2501".repeat(16)).append("\n");
            sb.append("*На что ушли деньги:*\n\n");
            for (CategoryStats cat : cats) {
                sb.append(cat.label)
                        .append("   *").append(fmt(cat.total)).append("*")
                        .append("  (").append(cat.percent).append("%)\n");
                // Подкатегории
                int subShow = Math.min(cat.subcats.size(), 4);
                for (int j = 0; j < subShow; j++) {
                    StatisticsService.SubcategoryAmount sub = cat.subcats.get(j);
                    String prefix = (j == subShow - 1) ? "   \u2514 " : "   \u251C ";
                    sb.append(prefix).append(sub.name).append(" \u2014 ").append(fmt(sub.amount)).append("\n");
                }
            }
        }

        // Сравнение с прошлым месяцем
        sb.append("\n").append("\u2501".repeat(16)).append("\n");
        if (prevExpenses.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal delta = prevExpenses.subtract(expenses);
            int deltaPct = delta.abs().multiply(java.math.BigDecimal.valueOf(100))
                    .divide(prevExpenses, 0, RoundingMode.HALF_UP).intValue();
            String prevMonthName = now.minusMonths(1).getMonth()
                    .getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("ru"));
            // Capitalize
            prevMonthName = prevMonthName.substring(0,1).toUpperCase() + prevMonthName.substring(1);

            sb.append("\uD83D\uDCC8 В ").append(prevMonthName.toLowerCase())
                    .append(" потратил *").append(fmt(prevExpenses)).append("*\n");
            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                sb.append("   В ").append(monthName.toLowerCase())
                        .append(" уже на ").append(deltaPct).append("% меньше — огонь \uD83D\uDD25\n");
            } else {
                int overPct = delta.abs().multiply(java.math.BigDecimal.valueOf(100))
                        .divide(prevExpenses, 0, RoundingMode.HALF_UP).intValue();
                sb.append("   В ").append(monthName.toLowerCase())
                        .append(" на ").append(overPct).append("% больше \uD83D\uDE2C\n");
            }
        }

        // Прогноз — контекстный, с оценкой
        if (dayOfMonth > 0 && expenses.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal projected = dailyAvg.multiply(java.math.BigDecimal.valueOf(daysInMonth));
            BigDecimal leftToSpend = dailyAvg.multiply(java.math.BigDecimal.valueOf(daysLeft));

            sb.append("\n");

            // Одна строка прогноза — просто и ясно
            if (income.compareTo(BigDecimal.ZERO) > 0) {
                int spendPct = projected.multiply(java.math.BigDecimal.valueOf(100))
                        .divide(income, 0, java.math.RoundingMode.HALF_UP).intValue();
                BigDecimal saving = income.subtract(projected);
                if (spendPct <= 85) {
                    sb.append("\uD83D\uDFE2 К концу месяца у тебя будет примерно *").append(fmt(saving)).append("*\n");
                } else if (spendPct <= 100) {
                    sb.append("\uD83D\uDFE1 К концу месяца у тебя будет примерно *").append(fmt(saving)).append("* — следи за темпом\n");
                } else {
                    BigDecimal over = projected.subtract(income);
                    sb.append("\uD83D\uDD34 К концу месяца уйдёшь в минус на *").append(fmt(over)).append("* — нужно притормозить\n");
                }
            } else {
                sb.append("\uD83D\uDD2E К концу месяца потратишь *~").append(fmt(projected)).append("*\n");
            }
        }

        // ── Умный инсайт ──
        sb.append("\n").append("\u2501".repeat(16)).append("\n");
        sb.append("\uD83E\uDDE0 *Главное за месяц:*\n\n");
        // Пробуем AI инсайт, при ошибке — шаблонный
        String insight = aiInsightService.generateMonthInsight(
                cats, income, expenses, diff, txs, dayOfMonth, daysInMonth, prevExpenses);
        if (insight == null) {
            insight = buildSmartInsight(user, cats, income, expenses, diff, txs);
        }
        sb.append(insight);

        return sb.toString();
    }

    /**
     * Умный инсайт — 6 сценариев по приоритету, всегда с реальными цифрами
     */
    private String buildSmartInsight(User user,
                                     List<CategoryStats> cats,
                                     BigDecimal income,
                                     BigDecimal expenses,
                                     BigDecimal diff,
                                     List<Transaction> txs) {
        // 1. Расходы > доходов — самый важный сигнал
        if (income.compareTo(BigDecimal.ZERO) > 0 && diff.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal over = diff.abs();
            String villain = cats.isEmpty() ? "Прочее" : cats.get(0).label;
            return "Потратил на *" + fmt(over) + "* больше чем заработал \uD83D\uDE2C\n"
                    + "Главный виновник — " + villain + ".\n"
                    + "Одна неделя без него закрывает этот минус.\n";
        }

        // 2. Доставка/Глово — считаем количество и сумму
        BigDecimal deliveryTotal = BigDecimal.ZERO;
        int deliveryCount = 0;
        for (Transaction tx : txs) {
            String desc = tx.getDescription() == null ? "" : tx.getDescription().toLowerCase();
            if (desc.contains("глово") || desc.contains("glovo") || desc.contains("доставка")
                    || desc.contains("яндекс еда") || desc.contains("delivery")) {
                deliveryTotal = deliveryTotal.add(tx.getAmount());
                deliveryCount++;
            }
        }
        if (deliveryCount >= 3 && deliveryTotal.compareTo(BigDecimal.valueOf(500)) > 0) {
            BigDecimal half = deliveryTotal.divide(java.math.BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
            BigDecimal year = half.multiply(java.math.BigDecimal.valueOf(12));
            return "Доставка — " + deliveryCount + " заказов на *" + fmt(deliveryTotal) + "* \uD83D\uDEF5\n"
                    + "Курьер тебя уже по имени знает, наверное.\n\n"
                    + "Закажи в 2 раза реже →\n"
                    + "сэкономишь *" + fmt(half) + "* в месяц.\n"
                    + "За год — *" + fmt(year) + "*. Это уже билет куда-нибудь ✈️\n";
        }

        // 3. Одна категория > 40% расходов
        if (!cats.isEmpty() && cats.get(0).percent > 40) {
            CategoryStats top = cats.get(0);
            BigDecimal saving = top.total.multiply(java.math.BigDecimal.valueOf(20))
                    .divide(java.math.BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            BigDecimal yearSaving = saving.multiply(java.math.BigDecimal.valueOf(12));
            String advice = "";
            if (top.label.contains("Еда") || top.label.contains("Кафе") || top.label.contains("Фастфуд"))
                advice = "Готовь дома хотя бы 3 дня в неделю.";
            else if (top.label.contains("Такси") || top.label.contains("Транспорт"))
                advice = "Попробуй маршрутку 2-3 раза в неделю.";
            else if (top.label.contains("Покупки"))
                advice = "Составляй список перед магазином — реально помогает.";
            else
                advice = "Стоит посмотреть куда конкретно уходят деньги.";
            return top.label + " забирает *" + top.percent + "%* всех расходов.\n"
                    + advice + "\n\n"
                    + "Срежь на 20% → *+" + fmt(saving) + "* в месяц\n"
                    + "За год — *+" + fmt(yearSaving) + "*\n";
        }

        // 4. Категория выросла > 30% vs прошлый месяц (такси)
        if (!cats.isEmpty()) {
            CategoryStats top = cats.get(0);
            // Simple check: if top category is taxi and percent is notable
            if ((top.label.contains("Такси")) && top.percent > 20) {
                BigDecimal saving = top.total.multiply(java.math.BigDecimal.valueOf(30))
                        .divide(java.math.BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                return "На такси ушло *" + fmt(top.total) + "* за месяц \uD83D\uDE95\n"
                        + "Это серьёзно.\n\n"
                        + "Пересаживайся на маршрутку хотя бы раз в день →\n"
                        + "сэкономишь *~" + fmt(saving) + "* в месяц\n";
            }
        }

        // 5. Хороший месяц — остаток > 20% дохода
        if (income.compareTo(BigDecimal.ZERO) > 0 && diff.compareTo(BigDecimal.ZERO) > 0) {
            int savePct = diff.multiply(java.math.BigDecimal.valueOf(100))
                    .divide(income, 0, RoundingMode.HALF_UP).intValue();
            if (savePct >= 20) {
                BigDecimal half = diff.divide(java.math.BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
                BigDecimal year = half.multiply(java.math.BigDecimal.valueOf(12));
                BigDecimal perDay = diff.divide(java.math.BigDecimal.valueOf(30), 0, RoundingMode.HALF_UP);
                return "Ты сохраняешь *" + savePct + "%* дохода — это круто \uD83D\uDCAA\n\n"
                        + "Свободных денег: *" + fmt(diff) + "*\n"
                        + "Это *" + fmt(perDay) + "* сом каждый день лишних.\n\n"
                        + "Отложи половину → *" + fmt(half) + "* в копилку\n"
                        + "12 таких месяцев = *" + fmt(year) + "* накоплений \uD83C\uDFAF\n";
            }
        }

        // 6. Стабильный месяц — нет аномалий
        if (dailyAvgFromExpenses(expenses, LocalDate.now(BISHKEK).getDayOfMonth()).compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal daily = dailyAvgFromExpenses(expenses, LocalDate.now(BISHKEK).getDayOfMonth());
            BigDecimal projected = daily.multiply(java.math.BigDecimal.valueOf(LocalDate.now(BISHKEK).lengthOfMonth()));
            return "Ровный месяц, без резких движений \uD83D\uDFE2\n"
                    + "Темп " + fmt(daily) + " сом/день — к концу месяца\n"
                    + "потратишь примерно *" + fmt(projected) + "*.\n"
                    + "Так держать \uD83D\uDC4D\n";
        }

        return "Продолжай записывать — скоро появятся умные советы \uD83E\uDDE0\n";
    }

    private BigDecimal dailyAvgFromExpenses(BigDecimal expenses, int days) {
        if (days <= 0 || expenses.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return expenses.divide(java.math.BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP);
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