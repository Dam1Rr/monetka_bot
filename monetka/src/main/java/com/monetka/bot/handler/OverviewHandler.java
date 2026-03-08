package com.monetka.bot.handler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
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

    public OverviewHandler(StatisticsService statisticsService,
                           BudgetService budgetService,
                           UserStateService stateService,
                           TransactionRepository transactionRepository,
                           CategoryRepository categoryRepository,
                           SubcategoryRepository subcategoryRepository,
                           PaydayService paydayService) {
        this.statisticsService     = statisticsService;
        this.budgetService         = budgetService;
        this.stateService          = stateService;
        this.transactionRepository = transactionRepository;
        this.categoryRepository    = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.paydayService         = paydayService;
    }

    // ================================================================
    // Entry point
    // ================================================================

    @Transactional
    public void handle(String action, User user, long chatId, MonetkaBot bot) {
        if      (action.equals("main"))                showMain(user, chatId, bot);
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
    // 1. MAIN OVERVIEW
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

        bot.sendMessage(chatId, sb.toString(), KeyboardFactory.overviewMain(cats, categoryRepository));
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

        bot.sendMessage(chatId, sb.toString(), KeyboardFactory.overviewGoals(goals, allCats));
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