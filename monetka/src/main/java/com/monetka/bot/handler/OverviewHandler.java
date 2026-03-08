package com.monetka.bot.handler;

import com.monetka.bot.MonetkaBot;
import com.monetka.service.ReportService;
import com.monetka.bot.keyboard.KeyboardFactory;
import java.time.format.TextStyle;
import com.monetka.model.*;
import com.monetka.model.enums.UserState;
import com.monetka.repository.*;
import com.monetka.service.*;
import com.monetka.service.PaydayService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Locale;

@Component
public class OverviewHandler {

    private static final ZoneId            BISHKEK  = ZoneId.of("Asia/Bishkek");
    private static final DateTimeFormatter SHORT    = DateTimeFormatter.ofPattern("dd MMM", new Locale("ru"));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final StatisticsService     statisticsService;
    private final BudgetService         budgetService;
    private final UserStateService      stateService;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository    categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final PaydayService         paydayService;
    private final ReportService         reportService;

    public OverviewHandler(StatisticsService statisticsService,
                           BudgetService budgetService,
                           UserStateService stateService,
                           TransactionRepository transactionRepository,
                           CategoryRepository categoryRepository,
                           SubcategoryRepository subcategoryRepository,
                           PaydayService paydayService,
                           ReportService reportService) {
        this.statisticsService     = statisticsService;
        this.budgetService         = budgetService;
        this.stateService          = stateService;
        this.transactionRepository = transactionRepository;
        this.categoryRepository    = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.paydayService         = paydayService;
        this.reportService         = reportService;
    }

    // ================================================================
    // Entry point
    // ================================================================

    @Transactional
    public void handle(String action, User user, long chatId, int messageId, MonetkaBot bot) {
        if      (action.equals("main"))                showMain(user, chatId, bot);
            // ── Tab navigation — edit existing message ──
        else if (action.equals("tab:today"))           showTabToday(user, chatId, messageId, bot);
        else if (action.equals("tab:week"))            showTabWeek(user, chatId, messageId, bot);
        else if (action.equals("tab:month"))           showTabMonth(user, chatId, messageId, bot);
        else if (action.equals("tab:period"))          showTabPeriod(user, chatId, messageId, bot);
        else if (action.equals("tab:limits"))          showTabLimits(user, chatId, messageId, bot);
            // ── Period quick picks ──
        else if (action.startsWith("period:"))         handlePeriodPick(user, action, chatId, messageId, bot);
            // ── Calendar ──
        else if (action.startsWith("cal:"))            handleCalendar(user, action, chatId, messageId, bot);
            // ── Existing drill-down ──
        else if (action.startsWith("cat:"))            showCategory(user, chatId, bot, parseId(action));
        else if (action.startsWith("subcat:"))         showSubcategory(user, chatId, bot, parseId(action));
        else if (action.startsWith("days:"))           showDays(user, chatId, bot, parseId(action));
        else if (action.equals("goals"))               showGoals(user, chatId, bot);
        else if (action.startsWith("set_goal:"))       startSetGoal(user, chatId, bot, parseId(action));
        else if (action.startsWith("del_goal:"))       deleteGoal(user, chatId, bot, parseId(action));
        else if (action.startsWith("view_tx:"))        showTxDetail(user, chatId, bot, parseId(action));
        else if (action.startsWith("del_tx:"))         confirmDeleteTx(user, chatId, bot, parseId(action));
        else if (action.startsWith("confirm_del:"))    doDeleteTx(user, chatId, bot, parseId(action));
        else if (action.startsWith("edit_amount:"))    startEditAmount(user, chatId, bot, parseId(action));
        else if (action.startsWith("edit_desc:"))      startEditDesc(user, chatId, bot, parseId(action));
    }

    // ================================================================
    // TAB: СЕГОДНЯ
    // ================================================================

    private void showTabToday(User user, long chatId, int msgId, MonetkaBot bot) {
        String text = reportService.buildTodayStats(user);
        bot.editMessage(chatId, msgId, text, KeyboardFactory.navTabs("today"));
    }

    // ================================================================
    // TAB: НЕДЕЛЯ
    // ================================================================

    private void showTabWeek(User user, long chatId, int msgId, MonetkaBot bot) {
        String text = reportService.buildWeekStats(user);
        bot.editMessage(chatId, msgId, text, KeyboardFactory.navTabs("week"));
    }

    // ================================================================
    // TAB: МЕСЯЦ
    // ================================================================

    private void showTabMonth(User user, long chatId, int msgId, MonetkaBot bot) {
        String text = reportService.buildMonthStats(user);
        // Month also gets category drill-down buttons + navtabs combined
        List<StatisticsService.CategoryStats> cats =
                statisticsService.getDetailedExpenses(user,
                        LocalDate.now(BISHKEK).withDayOfMonth(1).atStartOfDay(),
                        LocalDate.now(BISHKEK).withDayOfMonth(1).atStartOfDay().plusMonths(1));

        // Build combined keyboard: categories on top rows + navtabs last row
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new ArrayList<>();
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
        int show = Math.min(cats.size(), 6);
        for (int i = 0; i < show; i++) {
            StatisticsService.CategoryStats cs = cats.get(i);
            String name = cs.label.replaceAll("^[\\p{So}\\p{Sm}\\s]+", "").trim();
            categoryRepository.findByName(name).ifPresent(cat ->
                    row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                            .text(cs.label).callbackData("overview:cat:" + cat.getId()).build()));
            if (row.size() == 2) { rows.add(new ArrayList<>(row)); row.clear(); }
        }
        if (!row.isEmpty()) rows.add(new ArrayList<>(row));

        // Add navtabs row
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup navKb = KeyboardFactory.navTabs("month");
        rows.addAll(navKb.getKeyboard());
        bot.editMessage(chatId, msgId, text,
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    // ================================================================
    // TAB: ПЕРИОД — quick picks
    // ================================================================

    private void showTabPeriod(User user, long chatId, int msgId, MonetkaBot bot) {
        // Combine periodPicker rows + navtabs
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new ArrayList<>();
        rows.addAll(KeyboardFactory.periodPicker().getKeyboard());
        rows.addAll(KeyboardFactory.navTabs("period").getKeyboard());
        bot.editMessage(chatId, msgId,
                "\uD83D\uDCCA *Статистика за период*\n\nВыбери готовый или укажи свои даты:",
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handlePeriodPick(User user, String action, long chatId, int msgId, MonetkaBot bot) {
        String period = action.substring(7); // strip "period:"
        String text;
        switch (period) {
            case "today" -> text = reportService.buildTodayStats(user);
            case "week"  -> text = reportService.buildWeekStats(user);
            case "month" -> text = reportService.buildMonthStats(user);
            case "cal"   -> { showCalendar(chatId, msgId, bot, LocalDate.now(BISHKEK).getYear(),
                    LocalDate.now(BISHKEK).getMonthValue(), null, null); return; }
            default      -> text = "Неизвестный период";
        }
        // Result + period nav + navtabs
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new ArrayList<>();
        rows.addAll(KeyboardFactory.periodPicker().getKeyboard());
        rows.addAll(KeyboardFactory.navTabs("period").getKeyboard());
        bot.editMessage(chatId, msgId, text,
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    // ================================================================
    // CALENDAR — range picker
    // ================================================================

    private void showCalendar(long chatId, int msgId, MonetkaBot bot,
                              int year, int month, Integer startDay, Integer endDay) {
        bot.editMessage(chatId, msgId,
                "\uD83D\uDDD3 *Выбери период*\n\n_Тап — начало, второй тап — конец:_",
                KeyboardFactory.calendarMonth(year, month, startDay, endDay));
    }

    private void handleCalendar(User user, String action, long chatId, int msgId, MonetkaBot bot) {
        // action formats:
        //   cal:prev:year:month
        //   cal:next:year:month
        //   cal:day:year:month:day
        //   cal:confirm:year:month:start:end
        String[] parts = action.split(":");
        String sub = parts[1];

        if (sub.equals("prev") || sub.equals("next")) {
            int year  = Integer.parseInt(parts[2]);
            int month = Integer.parseInt(parts[3]);
            month = sub.equals("next") ? (month == 12 ? 1 : month + 1) : (month == 1 ? 12 : month - 1);
            year  = sub.equals("next") ? (month == 1 ? year + 1 : year) : (month == 12 ? year - 1 : year);
            showCalendar(chatId, msgId, bot, year, month, null, null);
            return;
        }

        if (sub.equals("day")) {
            int year  = Integer.parseInt(parts[2]);
            int month = Integer.parseInt(parts[3]);
            int day   = Integer.parseInt(parts[4]);
            // Store in user state: cal_start, cal_end
            String stored = stateService.getData(user.getTelegramId(), "cal_start");
            String storedEnd = stateService.getData(user.getTelegramId(), "cal_end");
            if (stored == null || (storedEnd != null && !storedEnd.isEmpty())) {
                // First tap — set start
                stateService.putData(user.getTelegramId(), "cal_start", year + ":" + month + ":" + day);
                // reset cal_end by setting fresh start
                showCalendar(chatId, msgId, bot, year, month, day, null);
            } else {
                // Second tap — set end
                String[] startParts = stored.split(":");
                int startDay = Integer.parseInt(startParts[2]);
                int endDay   = day;
                if (endDay < startDay) { int tmp = startDay; startDay = endDay; endDay = tmp; }
                stateService.putData(user.getTelegramId(), "cal_end", year + ":" + month + ":" + endDay);
                showCalendar(chatId, msgId, bot, year, month, startDay, endDay);
            }
            return;
        }

        if (sub.equals("confirm")) {
            int year  = Integer.parseInt(parts[2]);
            int month = Integer.parseInt(parts[3]);
            int start = Integer.parseInt(parts[4]);
            int end   = Integer.parseInt(parts[5]);
            stateService.putData(user.getTelegramId(), "cal_start", null);
            // reset cal_end by setting fresh start

            LocalDateTime from = LocalDate.of(year, month, start).atStartOfDay();
            LocalDateTime to   = LocalDate.of(year, month, end).plusDays(1).atStartOfDay();
            String monthName = from.getMonth().getDisplayName(
                    java.time.format.TextStyle.FULL_STANDALONE, new Locale("ru"));
            String label = start + "–" + end + " " + monthName;
            String text = reportService.buildRangeStats(user, from, to, label);

            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new ArrayList<>();
            rows.addAll(KeyboardFactory.periodPicker().getKeyboard());
            rows.addAll(KeyboardFactory.navTabs("period").getKeyboard());
            bot.editMessage(chatId, msgId, text,
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup.builder().keyboard(rows).build());
        }
    }

    // ================================================================
    // TAB: ЛИМИТЫ
    // ================================================================

    private void showTabLimits(User user, long chatId, int msgId, MonetkaBot bot) {
        List<BudgetGoal> goals   = budgetService.getGoals(user);
        List<Category>   allCats = categoryRepository.findAllByIsDefaultFalseOrderByName();

        // Tips that rotate
        String[] tips = {
                "Лимит — это не ограничение. Это разрешение тратить без чувства вины.",
                "Люди с бюджетом по категориям тратят на 20% меньше.",
                "Поставь лимит один раз — бот сам напомнит когда пора остановиться.",
                "Без лимитов деньги уходят незаметно. С лимитами — ты сам решаешь куда.",
        };
        String tip = tips[(int)(System.currentTimeMillis() / 60000 % tips.length)];

        StringBuilder sb = new StringBuilder("\uD83C\uDFAF *Лимиты на месяц*\n\n");
        sb.append("_").append(tip).append("_\n\n");

        if (goals.isEmpty()) {
            sb.append("Лимитов пока нет.\nВыбери категорию чтобы установить максимум:\n");
        } else {
            LocalDate now = LocalDate.now(BISHKEK);
            LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
            LocalDateTime to   = from.plusMonths(1);
            for (BudgetGoal g : goals) {
                BigDecimal spent = budgetService.getCategorySpentForPeriod(user, g.getCategory(), from, to);
                int pct = g.getAmount().compareTo(BigDecimal.ZERO) > 0
                        ? spent.multiply(BigDecimal.valueOf(100))
                        .divide(g.getAmount(), 0, RoundingMode.HALF_UP).intValue()
                        : 0;
                String icon = pct >= 100 ? "\uD83D\uDD34" : pct >= 80 ? "\uD83D\uDFE1" : "\u2705";
                BigDecimal left = g.getAmount().subtract(spent);
                String leftText = left.compareTo(BigDecimal.ZERO) >= 0
                        ? "осталось " + fmt(left)
                        : "превышение " + fmt(left.negate());
                if (g.getCategory().getEmoji() != null) sb.append(g.getCategory().getEmoji()).append(" ");
                sb.append("*").append(g.getCategory().getName()).append("*  ")
                        .append(icon).append("\n");
                sb.append(fmt(spent)).append(" / ").append(fmt(g.getAmount()))
                        .append("  _").append(leftText).append("_\n\n");
            }
        }

        // Build limits keyboard (edit buttons + add buttons) + navtabs
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new ArrayList<>();
        // Existing goals — edit buttons
        if (!goals.isEmpty()) {
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
            for (BudgetGoal g : goals) {
                String label = "✏️ " + (g.getCategory().getEmoji() != null ? g.getCategory().getEmoji() + " " : "") + g.getCategory().getName();
                row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text(label).callbackData("overview:set_goal:" + g.getCategory().getId()).build());
                if (row.size() == 2) { rows.add(new ArrayList<>(row)); row.clear(); }
            }
            if (!row.isEmpty()) rows.add(new ArrayList<>(row));
        }
        // Add new category buttons
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> addRow = new ArrayList<>();
        Set<Long> existingIds = new java.util.HashSet<>();
        goals.forEach(g -> existingIds.add(g.getCategory().getId()));
        for (Category cat : allCats) {
            if (existingIds.contains(cat.getId())) continue;
            String catLabel = (cat.getEmoji() != null ? cat.getEmoji() + " " : "") + cat.getName();
            addRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text(catLabel).callbackData("overview:set_goal:" + cat.getId()).build());
            if (addRow.size() == 2) { rows.add(new ArrayList<>(addRow)); addRow.clear(); }
        }
        if (!addRow.isEmpty()) rows.add(new ArrayList<>(addRow));
        // NavTabs
        rows.addAll(KeyboardFactory.navTabs("limits").getKeyboard());
        bot.editMessage(chatId, msgId, sb.toString(),
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    // ================================================================
    // 1. MAIN OVERVIEW
    // ================================================================

    // ================================================================

    @Transactional(readOnly = true)
    public void showMain(User user, long chatId, MonetkaBot bot) {
        LocalDate now   = LocalDate.now(BISHKEK);
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        BigDecimal income   = safe(statisticsService.getMonthIncome(user));
        BigDecimal expenses = safe(statisticsService.getMonthExpenses(user));
        BigDecimal diff     = income.subtract(expenses);

        int daysPassed  = now.getDayOfMonth();
        int daysInMonth = now.lengthOfMonth();
        int daysLeft    = daysInMonth - daysPassed;

        List<StatisticsService.CategoryStats> cats =
                statisticsService.getDetailedExpenses(user, from, to);
        List<BudgetGoal> goals = budgetService.getGoals(user);
        Map<Long, BigDecimal> goalMap = new HashMap<>();
        for (BudgetGoal g : goals) goalMap.put(g.getCategory().getId(), g.getAmount());

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCCA *").append(statisticsService.currentMonthName())
                .append(" ").append(now.getYear()).append("*\n");
        sb.append("_\u041f\u0440\u043e\u0448\u043b\u043e ").append(daysPassed)
                .append(" \u0434\u043d., \u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c ").append(daysLeft).append("_\n\n");

        // Today's spending
        LocalDateTime todayStart = now.atStartOfDay();
        LocalDateTime todayEnd   = now.plusDays(1).atStartOfDay();
        BigDecimal todayExp = safe(statisticsService.getMonthExpensesForPeriod(user, todayStart, todayEnd));
        BigDecimal todayInc = safe(statisticsService.getMonthIncomeForPeriod(user, todayStart, todayEnd));

        if (income.compareTo(BigDecimal.ZERO) > 0)
            sb.append("\uD83D\uDCB0 \u0414\u043e\u0445\u043e\u0434\u044b:   *+").append(fmt(income)).append("*\n");
        sb.append("\uD83D\uDCB8 \u0420\u0430\u0441\u0445\u043e\u0434\u044b:  *\u2212").append(fmt(expenses)).append("*\n");

        // Today line — only show if there's activity today
        if (todayExp.compareTo(java.math.BigDecimal.ZERO) > 0 || todayInc.compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append("\uD83D\uDCC6 \u0421\u0435\u0433\u043e\u0434\u043d\u044f:    ");
            if (todayInc.compareTo(java.math.BigDecimal.ZERO) > 0)
                sb.append("*+").append(fmt(todayInc)).append("*");
            if (todayExp.compareTo(java.math.BigDecimal.ZERO) > 0) {
                if (todayInc.compareTo(java.math.BigDecimal.ZERO) > 0) sb.append("  ");
                sb.append("*\u2212").append(fmt(todayExp)).append("*");
            }
            sb.append("\n");
        }
        sb.append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "\u2705" : "\u26A0\uFE0F");
        sb.append(" \u0411\u0430\u043b\u0430\u043d\u0441:   *").append(fmt(diff)).append("*\n");

        if (!cats.isEmpty()) {
            sb.append("\n*\u0422\u043e\u043f \u0440\u0430\u0441\u0445\u043e\u0434\u043e\u0432:*\n");
            int show = Math.min(cats.size(), 5);
            for (int i = 0; i < show; i++) {
                StatisticsService.CategoryStats cat = cats.get(i);
                sb.append("\n").append(i + 1).append(". ").append(cat.label);
                sb.append("  *").append(fmt(cat.total)).append("*  (").append(cat.percent).append("%)");
                // Goal indicator — just icon, no bar
                categoryRepository.findByName(extractName(cat.label)).ifPresent(c -> {
                    BigDecimal goal = goalMap.get(c.getId());
                    if (goal != null && goal.compareTo(BigDecimal.ZERO) > 0) {
                        int pct = cat.total.multiply(BigDecimal.valueOf(100))
                                .divide(goal, 0, RoundingMode.HALF_UP).intValue();
                        sb.append(pct >= 100 ? "  \uD83D\uDD34" : pct >= 80 ? "  \uD83D\uDFE1" : "  \u2705");
                    }
                });
            }
            if (cats.size() > 5)
                sb.append("\n_... \u0438 \u0435\u0449\u0451 ").append(cats.size() - 5).append(" \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u0439_");
        } else {
            sb.append("\n_\u0420\u0430\u0441\u0445\u043e\u0434\u043e\u0432 \u043f\u043e\u043a\u0430 \u043d\u0435\u0442 \uD83C\uDF31_");
        }

        // Append payday smart analysis if cycle active
        paydayService.getSmartAnalysis(user).ifPresent(sb::append);

        bot.sendMessage(chatId, sb.toString(), KeyboardFactory.navTabs("month"));
    }

    // ================================================================
    // 2. CATEGORY
    // ================================================================

    @Transactional(readOnly = true)
    public void showCategory(User user, long chatId, MonetkaBot bot, long categoryId) {
        Category cat = categoryRepository.findById(categoryId).orElse(null);
        if (cat == null) { bot.sendText(chatId, "\u041a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044f \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430."); return; }

        LocalDate now   = LocalDate.now(BISHKEK);
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        BigDecimal spent = budgetService.getCategorySpentForPeriod(user, cat, from, to);
        List<Object[]> subcats = transactionRepository.sumBySubcategoryInCategory(user, categoryId, from, to);
        List<Transaction> txs = transactionRepository.findExpensesByCategoryAndPeriod(user, categoryId, from, to);

        StringBuilder sb = new StringBuilder();
        sb.append(cat.getEmoji() != null ? cat.getEmoji() + " " : "");
        sb.append("*").append(cat.getName()).append(" \u2014 ").append(statisticsService.currentMonthName()).append("*\n\n");
        sb.append("\u041f\u043e\u0442\u0440\u0430\u0447\u0435\u043d\u043e: *").append(fmt(spent)).append("*\n");

        // Goal — simple text, no bar
        budgetService.getGoal(user, cat).ifPresent(goal -> {
            BigDecimal remaining = goal.getAmount().subtract(spent);
            int pct = spent.compareTo(BigDecimal.ZERO) > 0
                    ? spent.multiply(BigDecimal.valueOf(100))
                    .divide(goal.getAmount(), 0, RoundingMode.HALF_UP).intValue()
                    : 0;
            sb.append("\u0426\u0435\u043b\u044c: *").append(fmt(goal.getAmount())).append("*");
            sb.append("  (").append(pct).append("%)");
            if (pct >= 100)
                sb.append("  \uD83D\uDD34 \u043f\u0440\u0435\u0432\u044b\u0448\u0435\u043d\u0430 \u043d\u0430 *").append(fmt(remaining.abs())).append("*");
            else if (pct >= 80)
                sb.append("  \uD83D\uDFE1 \u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c *").append(fmt(remaining)).append("*");
            else
                sb.append("  \u2705 \u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c *").append(fmt(remaining)).append("*");
            sb.append("\n");
        });

        // Subcategories
        if (!subcats.isEmpty()) {
            sb.append("\n\uD83D\uDCC2 *\u041F\u043e \u043f\u043e\u0434\u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044f\u043c:*\n");
            for (Object[] row : subcats) {
                String sName  = (String) row[0];
                String sEmoji = (String) row[1];
                BigDecimal amt = (BigDecimal) row[2];
                int pct = spent.compareTo(BigDecimal.ZERO) > 0
                        ? amt.multiply(BigDecimal.valueOf(100))
                        .divide(spent, 0, RoundingMode.HALF_UP).intValue()
                        : 0;
                sb.append(sEmoji != null ? sEmoji + " " : "\u2022 ");
                sb.append(sName).append("  *").append(fmt(amt)).append("*  (").append(pct).append("%)\n");
            }
        }

        // Last transactions
        if (!txs.isEmpty()) {
            sb.append("\n\uD83D\uDCCB *\u041F\u043E\u0441\u043B\u0435\u0434\u043D\u0438\u0435 \u0437\u0430\u043F\u0438\u0441\u0438:*\n");
            int show = Math.min(txs.size(), 5);
            for (int i = 0; i < show; i++) {
                Transaction tx = txs.get(i);
                sb.append(tx.getCreatedAt().toLocalDate().format(SHORT))
                        .append("  ").append(tx.getDescription())
                        .append("  \u2212").append(fmt(tx.getAmount())).append("\n");
            }
            if (txs.size() > 5)
                sb.append("_... \u0438 \u0435\u0449\u0451 ").append(txs.size() - 5).append(" \u0437\u0430\u043f\u0438\u0441\u0435\u0439_\n");
        }

        bot.sendMessage(chatId, sb.toString(),
                KeyboardFactory.overviewCategory(categoryId, !subcats.isEmpty(),
                        budgetService.getGoal(user, cat).isPresent()));
    }

    // ================================================================
    // 3. SUBCATEGORY
    // ================================================================

    @Transactional(readOnly = true)
    public void showSubcategory(User user, long chatId, MonetkaBot bot, long subcategoryId) {
        Subcategory sub = subcategoryRepository.findById(subcategoryId).orElse(null);
        if (sub == null) { bot.sendText(chatId, "\u041f\u043e\u0434\u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044f \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430."); return; }

        LocalDate now   = LocalDate.now(BISHKEK);
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        List<Transaction> txs = transactionRepository.findExpensesBySubcategoryAndPeriod(user, subcategoryId, from, to);
        BigDecimal total = txs.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder();
        sb.append(sub.getEmoji() != null ? sub.getEmoji() + " " : "");
        sb.append("*").append(sub.getName()).append("*\n\n");
        sb.append("\u0412\u0441\u0435\u0433\u043e: *").append(fmt(total)).append("*  \u2022  ").append(txs.size()).append(" \u0437\u0430\u043f\u0438\u0441\u0435\u0439\n");

        if (!txs.isEmpty()) {
            BigDecimal avg = total.divide(BigDecimal.valueOf(txs.size()), 0, RoundingMode.HALF_UP);
            sb.append("\u0421\u0440\u0435\u0434\u043d\u0438\u0439 \u0447\u0435\u043a: *").append(fmt(avg)).append("*\n");
        }

        if (!txs.isEmpty()) {
            sb.append("\n");
            int show = Math.min(txs.size(), 8);
            for (int i = 0; i < show; i++) {
                Transaction tx = txs.get(i);
                sb.append(tx.getCreatedAt().toLocalDate().format(SHORT))
                        .append("  ").append(tx.getDescription())
                        .append("  *\u2212").append(fmt(tx.getAmount())).append("*\n");
            }
        }

        Long catId = sub.getCategory() != null ? sub.getCategory().getId() : null;
        bot.sendMessage(chatId, sb.toString(),
                KeyboardFactory.overviewSubcategory(subcategoryId, catId, txs));
    }

    // ================================================================
    // 4. DAYS
    // ================================================================

    @Transactional(readOnly = true)
    public void showDays(User user, long chatId, MonetkaBot bot, long categoryId) {
        Category cat = categoryRepository.findById(categoryId).orElse(null);
        if (cat == null) { bot.sendText(chatId, "\u041a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044f \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430."); return; }

        LocalDate now   = LocalDate.now(BISHKEK);
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        List<Transaction> txs = transactionRepository.findExpensesByCategoryAndPeriod(user, categoryId, from, to);

        Map<LocalDate, BigDecimal>    byDay  = new LinkedHashMap<>();
        Map<LocalDate, List<String>>  descs  = new LinkedHashMap<>();
        for (Transaction tx : txs) {
            LocalDate d = tx.getCreatedAt().toLocalDate();
            byDay.merge(d, tx.getAmount(), BigDecimal::add);
            descs.computeIfAbsent(d, k -> new ArrayList<>()).add(tx.getDescription());
        }

        if (byDay.isEmpty()) {
            bot.sendMessage(chatId, "\u0417\u0430\u043f\u0438\u0441\u0435\u0439 \u043f\u043e\u043a\u0430 \u043d\u0435\u0442 \uD83C\uDF31",
                    KeyboardFactory.backToCategory(categoryId));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(cat.getEmoji() != null ? cat.getEmoji() + " " : "");
        sb.append("*").append(cat.getName()).append(" \u043f\u043e \u0434\u043d\u044f\u043c*\n\n");

        new TreeMap<>(byDay).forEach((day, amt) -> {
            sb.append(day.format(SHORT)).append("  *\u2212").append(fmt(amt)).append("*");
            List<String> ds = descs.get(day);
            if (ds != null && ds.size() == 1) sb.append("  _").append(ds.get(0)).append("_");
            else if (ds != null && ds.size() > 1) sb.append("  _(").append(ds.size()).append(" \u0437\u0430\u043f\u0438\u0441\u0438)_");
            sb.append("\n");
        });

        bot.sendMessage(chatId, sb.toString(), KeyboardFactory.backToCategory(categoryId));
    }

    // ================================================================
    // 5. GOALS
    // ================================================================

    @Transactional(readOnly = true)
    public void showGoals(User user, long chatId, MonetkaBot bot) {
        List<BudgetGoal> goals  = budgetService.getGoals(user);
        List<Category>   allCats = categoryRepository.findAllByIsDefaultFalseOrderByName();

        // Rotating tips
        String[] tips = {
                "Люди с бюджетом по категориям тратят на 20% меньше. Просто потому что видят куда уходят деньги.",
                "Лимит — это не ограничение. Это разрешение тратить без чувства вины.",
                "Поставь лимит один раз — и бот сам напомнит когда пора остановиться.",
                "Без лимитов деньги уходят незаметно. С лимитами — ты сам решаешь куда.",
                "Месяц с лимитами и месяц без — разница в кошельке будет видна сразу."
        };
        String tip = tips[(int)(System.currentTimeMillis() / 60000 % tips.length)];

        StringBuilder sb = new StringBuilder("🎯 *Лимиты на месяц*\n\n");
        sb.append("_").append(tip).append("_\n\n");

        if (goals.isEmpty()) {
            sb.append("Лимитов пока нет.\nВыбери категорию чтобы установить максимум:\n");
        } else {
            LocalDate now   = LocalDate.now(BISHKEK);
            LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
            LocalDateTime to   = from.plusMonths(1);

            for (BudgetGoal g : goals) {
                BigDecimal spent = budgetService.getCategorySpentForPeriod(user, g.getCategory(), from, to);
                int pct = g.getAmount().compareTo(BigDecimal.ZERO) > 0
                        ? spent.multiply(BigDecimal.valueOf(100))
                        .divide(g.getAmount(), 0, RoundingMode.HALF_UP).intValue()
                        : 0;
                String icon = pct >= 100 ? "\uD83D\uDD34" : pct >= 80 ? "\uD83D\uDFE1" : "\u2705";
                sb.append(g.getCategory().getEmoji() != null ? g.getCategory().getEmoji() + " " : "");
                sb.append("*").append(g.getCategory().getName()).append("*\n");
                sb.append(icon).append(" ").append(fmt(spent)).append(" / ").append(fmt(g.getAmount()));
                sb.append("  (").append(pct).append("%)\n\n");
            }
        }

        // Combine goals keyboard + navtabs
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> goalsRows = new ArrayList<>();
        goalsRows.addAll(KeyboardFactory.overviewGoals(goals, allCats).getKeyboard());
        goalsRows.addAll(KeyboardFactory.navTabs("limits").getKeyboard());
        bot.sendMessage(chatId, sb.toString(),
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup.builder().keyboard(goalsRows).build());
    }

    // ================================================================
    // 6. SET GOAL
    // ================================================================

    @Transactional
    public void startSetGoal(User user, long chatId, MonetkaBot bot, long categoryId) {
        Category cat = categoryRepository.findById(categoryId).orElse(null);
        if (cat == null) { bot.sendText(chatId, "\u041a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044f \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430."); return; }

        BigDecimal suggested = budgetService.suggestGoal(user, cat);
        String catLabel = (cat.getEmoji() != null ? cat.getEmoji() + " " : "") + cat.getName();

        stateService.setState(user.getTelegramId(), UserState.WAITING_GOAL_AMOUNT);
        stateService.putData(user.getTelegramId(), "goal_category_id", String.valueOf(categoryId));

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83C\uDFAF *\u0426\u0435\u043b\u044c \u2014 ").append(catLabel).append("*\n\n");

        budgetService.getGoal(user, cat).ifPresent(g ->
                sb.append("\u0422\u0435\u043a\u0443\u0449\u0430\u044f \u0446\u0435\u043b\u044c: *").append(fmt(g.getAmount())).append("*\n\n"));

        if (suggested.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("\uD83D\uDCA1 \u041f\u0440\u0435\u0434\u043b\u0430\u0433\u0430\u044e: *").append(fmt(suggested)).append("*\n");
            sb.append("_(\u043d\u0430 \u043e\u0441\u043d\u043e\u0432\u0435 \u0442\u0432\u043e\u0435\u0439 \u0438\u0441\u0442\u043e\u0440\u0438\u0438)_\n\n");
        }
        sb.append("\u0412\u0432\u0435\u0434\u0438 \u0441\u0443\u043c\u043c\u0443 \u0432 \u0441\u043e\u043c\u0430\u0445:");

        bot.sendMessage(chatId, sb.toString(), KeyboardFactory.cancelMenu());
    }

    // ================================================================
    // 7. DELETE GOAL
    // ================================================================

    @Transactional
    public void deleteGoal(User user, long chatId, MonetkaBot bot, long categoryId) {
        Category cat = categoryRepository.findById(categoryId).orElse(null);
        if (cat == null) return;
        budgetService.deleteGoal(user, cat);
        bot.sendMarkdown(chatId, "\uD83D\uDDD1 \u0426\u0435\u043b\u044c *" + cat.getName() + "* \u0443\u0434\u0430\u043b\u0435\u043d\u0430.");
        showGoals(user, chatId, bot);
    }

    // ================================================================
    // 8. TRANSACTION DETAIL + EDIT
    // ================================================================

    @Transactional(readOnly = true)
    public void showTxDetail(User user, long chatId, MonetkaBot bot, long txId) {
        transactionRepository.findById(txId).ifPresentOrElse(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            String catName = tx.getCategory() != null ? tx.getCategory().getDisplayName() : "\u2014";
            bot.sendMessage(chatId,
                    "\uD83D\uDCDD *" + tx.getDescription() + "*\n\n" +
                            "\uD83D\uDCB8 \u2212" + fmt(tx.getAmount()) + "\n" +
                            "\uD83C\uDFF7 " + catName + "\n" +
                            "\uD83D\uDCC5 " + tx.getCreatedAt().toLocalDate().format(DATE_FMT) + "\n\n" +
                            "\u0427\u0442\u043e \u0445\u043e\u0447\u0435\u0448\u044c \u0441\u0434\u0435\u043b\u0430\u0442\u044c?",
                    KeyboardFactory.editTxOptions(txId));
        }, () -> bot.sendText(chatId, "\u0417\u0430\u043f\u0438\u0441\u044c \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430."));
    }

    @Transactional
    public void confirmDeleteTx(User user, long chatId, MonetkaBot bot, long txId) {
        transactionRepository.findById(txId).ifPresentOrElse(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            String catName = tx.getCategory() != null ? tx.getCategory().getName() : "\u2014";
            bot.sendMessage(chatId,
                    "\uD83D\uDDD1 *\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u0437\u0430\u043f\u0438\u0441\u044c?*\n\n" +
                            "\uD83D\uDCDD " + tx.getDescription() + "\n" +
                            "\uD83D\uDCB8 \u2212" + fmt(tx.getAmount()) + "\n" +
                            "\uD83D\uDCC5 " + tx.getCreatedAt().toLocalDate().format(DATE_FMT),
                    KeyboardFactory.confirmDeleteTx(txId));
        }, () -> bot.sendText(chatId, "\u0417\u0430\u043f\u0438\u0441\u044c \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430."));
    }

    @Transactional
    public void doDeleteTx(User user, long chatId, MonetkaBot bot, long txId) {
        transactionRepository.findById(txId).ifPresentOrElse(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) {
                bot.sendText(chatId, "\u041d\u0435\u0442 \u0434\u043e\u0441\u0442\u0443\u043f\u0430.");
                return;
            }
            user.setBalance(user.getBalance().add(tx.getAmount()));
            transactionRepository.delete(tx);
            bot.sendMessage(chatId,
                    "\u2705 \u0417\u0430\u043f\u0438\u0441\u044c \u0443\u0434\u0430\u043b\u0435\u043d\u0430.\n\u0411\u0430\u043b\u0430\u043d\u0441 \u0432\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d: *" + fmt(user.getBalance()) + "*",
                    KeyboardFactory.mainMenu());
        }, () -> bot.sendText(chatId, "\u0417\u0430\u043f\u0438\u0441\u044c \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430."));
    }

    @Transactional
    public void startEditAmount(User user, long chatId, MonetkaBot bot, long txId) {
        transactionRepository.findById(txId).ifPresentOrElse(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            stateService.setState(user.getTelegramId(), UserState.WAITING_EDIT_AMOUNT);
            stateService.putData(user.getTelegramId(), "edit_tx_id", String.valueOf(txId));
            bot.sendMessage(chatId,
                    "\uD83D\uDCB8 *\u0418\u0437\u043c\u0435\u043d\u0438\u0442\u044c \u0441\u0443\u043c\u043c\u0443*\n\n" +
                            "\u0422\u0435\u043a\u0443\u0449\u0430\u044f: *" + fmt(tx.getAmount()) + "*\n\n" +
                            "\u0412\u0432\u0435\u0434\u0438 \u043d\u043e\u0432\u0443\u044e \u0441\u0443\u043c\u043c\u0443:",
                    KeyboardFactory.cancelMenu());
        }, () -> bot.sendText(chatId, "\u0417\u0430\u043f\u0438\u0441\u044c \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430."));
    }

    @Transactional
    public void startEditDesc(User user, long chatId, MonetkaBot bot, long txId) {
        transactionRepository.findById(txId).ifPresentOrElse(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            stateService.setState(user.getTelegramId(), UserState.WAITING_EDIT_DESCRIPTION);
            stateService.putData(user.getTelegramId(), "edit_tx_id", String.valueOf(txId));
            bot.sendMessage(chatId,
                    "\uD83D\uDCDD *\u0418\u0437\u043c\u0435\u043d\u0438\u0442\u044c \u043e\u043f\u0438\u0441\u0430\u043d\u0438\u0435*\n\n" +
                            "\u0422\u0435\u043a\u0443\u0449\u0435\u0435: *" + tx.getDescription() + "*\n\n" +
                            "\u0412\u0432\u0435\u0434\u0438 \u043d\u043e\u0432\u043e\u0435 \u043e\u043f\u0438\u0441\u0430\u043d\u0438\u0435:",
                    KeyboardFactory.cancelMenu());
        }, () -> bot.sendText(chatId, "\u0437\u0430\u043f\u0438\u0441\u044c \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430."));
    }

    @Transactional
    public boolean handleEditAmountInput(User user, String text, long chatId, MonetkaBot bot) {
        String txIdStr = stateService.getData(user.getTelegramId(), "edit_tx_id");
        if (txIdStr == null) { stateService.reset(user.getTelegramId()); return false; }

        BigDecimal newAmount;
        try {
            newAmount = new BigDecimal(text.replace(",", ".").replace(" ", ""));
            if (newAmount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            bot.sendMessage(chatId, "\u0412\u0432\u0435\u0434\u0438 \u0447\u0438\u0441\u043b\u043e, \u043d\u0430\u043f\u0440\u0438\u043c\u0435\u0440: *1500*", KeyboardFactory.cancelMenu());
            return true;
        }

        transactionRepository.findById(Long.parseLong(txIdStr)).ifPresent(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            BigDecimal diff = newAmount.subtract(tx.getAmount());
            if (tx.getType().name().equals("EXPENSE")) user.setBalance(user.getBalance().subtract(diff));
            else user.setBalance(user.getBalance().add(diff));
            tx.setAmount(newAmount);
            transactionRepository.save(tx);
            stateService.reset(user.getTelegramId());
            bot.sendMessage(chatId,
                    "\u2705 *\u0421\u0443\u043c\u043c\u0430 \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0430!*\n\n" +
                            "\uD83D\uDCDD " + tx.getDescription() + "\n" +
                            "\uD83D\uDCB8 \u2212" + fmt(newAmount) + "\n" +
                            "\uD83D\uDCB3 \u0411\u0430\u043b\u0430\u043d\u0441: *" + fmt(user.getBalance()) + "*",
                    KeyboardFactory.mainMenu());
        });
        return true;
    }

    @Transactional
    public boolean handleEditDescInput(User user, String text, long chatId, MonetkaBot bot) {
        String txIdStr = stateService.getData(user.getTelegramId(), "edit_tx_id");
        if (txIdStr == null) { stateService.reset(user.getTelegramId()); return false; }
        if (text.isBlank()) {
            bot.sendMessage(chatId, "\u041e\u043f\u0438\u0441\u0430\u043d\u0438\u0435 \u043d\u0435 \u043c\u043e\u0436\u0435\u0442 \u0431\u044b\u0442\u044c \u043f\u0443\u0441\u0442\u044b\u043c", KeyboardFactory.cancelMenu());
            return true;
        }
        transactionRepository.findById(Long.parseLong(txIdStr)).ifPresent(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            tx.setDescription(text.trim());
            transactionRepository.save(tx);
            stateService.reset(user.getTelegramId());
            bot.sendMessage(chatId,
                    "\u2705 *\u041e\u043f\u0438\u0441\u0430\u043d\u0438\u0435 \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u043e!*\n\n" +
                            "\uD83D\uDCDD " + text.trim() + "\n" +
                            "\uD83D\uDCB8 \u2212" + fmt(tx.getAmount()),
                    KeyboardFactory.mainMenu());
        });
        return true;
    }

    @Transactional
    public boolean handleGoalAmountInput(User user, String text, long chatId, MonetkaBot bot) {
        String catIdStr = stateService.getData(user.getTelegramId(), "goal_category_id");
        if (catIdStr == null) { stateService.reset(user.getTelegramId()); return false; }

        BigDecimal amount;
        try {
            amount = new BigDecimal(text.replace(",", ".").replace(" ", ""));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            bot.sendMessage(chatId, "\u0412\u0432\u0435\u0434\u0438 \u0447\u0438\u0441\u043b\u043e, \u043d\u0430\u043f\u0440\u0438\u043c\u0435\u0440: *15000*", KeyboardFactory.cancelMenu());
            return true;
        }

        Category cat = categoryRepository.findById(Long.parseLong(catIdStr)).orElse(null);
        if (cat == null) { stateService.reset(user.getTelegramId()); return false; }

        budgetService.setGoal(user, cat, amount);
        stateService.reset(user.getTelegramId());
        String catLabel = (cat.getEmoji() != null ? cat.getEmoji() + " " : "") + cat.getName();
        bot.sendMessage(chatId,
                "\u2705 *\u0426\u0435\u043b\u044c \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u0430!*\n\n" +
                        catLabel + "  \u2192  *" + fmt(amount) + " / \u043c\u0435\u0441*\n\n" +
                        "\uD83D\uDCA1 \u041d\u0430\u043f\u043e\u043c\u043d\u044e \u043f\u0440\u0438 80%, 90% \u0438 100%",
                KeyboardFactory.mainMenu());
        return true;
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String extractName(String label) {
        return label.replaceAll("^[\\p{So}\\p{Sm}\\s]+", "").trim();
    }

    private long parseId(String action) {
        return Long.parseLong(action.split(":")[1]);
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private String fmt(BigDecimal v) { return String.format("%,.0f \u0441\u043e\u043c", v); }
}