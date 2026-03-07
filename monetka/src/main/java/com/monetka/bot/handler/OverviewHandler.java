package com.monetka.bot.handler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.*;
import com.monetka.model.enums.UserState;
import com.monetka.repository.*;
import com.monetka.service.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Handles all overview:* callbacks — the main financial dashboard
 * with drill-down: category → subcategory → transaction list.
 *
 * Callback format:  overview:<action>[:<param>]
 */
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

    public OverviewHandler(StatisticsService statisticsService,
                           BudgetService budgetService,
                           UserStateService stateService,
                           TransactionRepository transactionRepository,
                           CategoryRepository categoryRepository,
                           SubcategoryRepository subcategoryRepository) {
        this.statisticsService     = statisticsService;
        this.budgetService         = budgetService;
        this.stateService          = stateService;
        this.transactionRepository = transactionRepository;
        this.categoryRepository    = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
    }

    // ================================================================
    // Entry point from CallbackHandler
    // ================================================================

    @Transactional
    public void handle(String action, User user, long chatId, MonetkaBot bot) {
        if (action.equals("main"))                         showMain(user, chatId, bot);
        else if (action.startsWith("cat:"))                showCategory(user, chatId, bot, parseId(action));
        else if (action.startsWith("subcat:"))             showSubcategory(user, chatId, bot, parseId(action));
        else if (action.startsWith("days:"))               showDays(user, chatId, bot, parseId(action));
        else if (action.equals("goals"))                   showGoals(user, chatId, bot);
        else if (action.startsWith("set_goal:"))           startSetGoal(user, chatId, bot, parseId(action));
        else if (action.startsWith("del_goal:"))           deleteGoal(user, chatId, bot, parseId(action));
        else if (action.startsWith("del_tx:"))             confirmDeleteTx(user, chatId, bot, parseId(action));
        else if (action.startsWith("confirm_del:"))        doDeleteTx(user, chatId, bot, parseId(action));
        else if (action.startsWith("edit_amount:"))        startEditAmount(user, chatId, bot, parseId(action));
        else if (action.startsWith("edit_desc:"))          startEditDesc(user, chatId, bot, parseId(action));
        else if (action.startsWith("view_tx:"))            showTxDetail(user, chatId, bot, parseId(action));
    }

    // ================================================================
    // 1. MAIN OVERVIEW
    // ================================================================

    @Transactional(readOnly = true)
    public void showMain(User user, long chatId, MonetkaBot bot) {
        LocalDate now  = LocalDate.now(BISHKEK);
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        BigDecimal income   = safe(statisticsService.getMonthIncome(user));
        BigDecimal expenses = safe(statisticsService.getMonthExpenses(user));
        BigDecimal diff     = income.subtract(expenses);

        // Days passed / remaining
        int daysPassed   = now.getDayOfMonth();
        int daysInMonth  = now.lengthOfMonth();
        int daysLeft     = daysInMonth - daysPassed;

        // Forecast
        BigDecimal forecast = BigDecimal.ZERO;
        if (daysPassed > 0 && expenses.compareTo(BigDecimal.ZERO) > 0) {
            forecast = expenses
                    .multiply(BigDecimal.valueOf(daysInMonth))
                    .divide(BigDecimal.valueOf(daysPassed), 0, RoundingMode.HALF_UP);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *").append(statisticsService.currentMonthName()).append(" ").append(now.getYear()).append("*\n");
        sb.append("_Прошло ").append(daysPassed).append(" дней, осталось ").append(daysLeft).append("_\n\n");

        if (income.compareTo(BigDecimal.ZERO) > 0)
            sb.append("💰 Доходы:   *+").append(fmt(income)).append("*\n");
        sb.append("💸 Расходы:  *−").append(fmt(expenses)).append("*\n");
        sb.append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "✅" : "⚠️");
        sb.append(" Остаток:  *").append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "").append(fmt(diff)).append("*\n");

        if (forecast.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("\n📈 *Прогноз на конец месяца:* ~").append(fmt(forecast)).append("\n");
        }

        // Top categories with goal progress
        List<StatisticsService.CategoryStats> cats =
                statisticsService.getDetailedExpenses(user, from, to);
        List<BudgetGoal> goals = budgetService.getGoals(user);
        Map<Long, BigDecimal> goalMap = new HashMap<>();
        for (BudgetGoal g : goals) goalMap.put(g.getCategory().getId(), g.getAmount());

        if (!cats.isEmpty()) {
            sb.append("\n*Топ расходов:*\n");
            int show = Math.min(cats.size(), 5);
            for (int i = 0; i < show; i++) {
                StatisticsService.CategoryStats cat = cats.get(i);
                sb.append("\n").append(i + 1).append(". ").append(cat.label);
                sb.append("  *").append(fmt(cat.total)).append("*");
                sb.append("  _(").append(cat.percent).append("%)_");

                // Find categoryId to check goal
                categoryRepository.findByName(extractName(cat.label)).ifPresent(c -> {
                    BigDecimal goal = goalMap.get(c.getId());
                    if (goal != null && goal.compareTo(BigDecimal.ZERO) > 0) {
                        int pct = cat.total.multiply(BigDecimal.valueOf(100))
                                .divide(goal, 0, RoundingMode.HALF_UP).intValue();
                        sb.append("  ").append(goalBar(pct));
                    }
                });
                sb.append("\n");
            }
            if (cats.size() > 5) {
                sb.append("_...и ещё ").append(cats.size() - 5).append(" категорий_\n");
            }
        } else {
            sb.append("\n_Расходов пока нет 🌱_\n");
        }

        bot.sendMessage(chatId, sb.toString(), KeyboardFactory.overviewMain(cats, categoryRepository));
    }

    // ================================================================
    // 2. CATEGORY DRILL-DOWN
    // ================================================================

    @Transactional(readOnly = true)
    public void showCategory(User user, long chatId, MonetkaBot bot, long categoryId) {
        Category cat = categoryRepository.findById(categoryId).orElse(null);
        if (cat == null) { bot.sendText(chatId, "Категория не найдена."); return; }

        LocalDate now  = LocalDate.now(BISHKEK);
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        BigDecimal spent = budgetService.getCategorySpentForPeriod(user, cat, from, to);

        StringBuilder sb = new StringBuilder();
        sb.append(cat.getEmoji() != null ? cat.getEmoji() + " " : "");
        sb.append("*").append(cat.getName()).append(" — ").append(statisticsService.currentMonthName()).append("*\n");
        sb.append("━━━━━━━━━━━━━━━━\n");
        sb.append("Потрачено:  *").append(fmt(spent)).append("*\n");

        // Goal progress
        budgetService.getGoal(user, cat).ifPresent(goal -> {
            BigDecimal remaining = goal.getAmount().subtract(spent);
            int pct = spent.compareTo(BigDecimal.ZERO) > 0
                    ? spent.multiply(BigDecimal.valueOf(100))
                    .divide(goal.getAmount(), 0, RoundingMode.HALF_UP).intValue()
                    : 0;
            sb.append("Цель:       *").append(fmt(goal.getAmount())).append("*\n");
            sb.append(progressBar(pct)).append("  ").append(pct).append("%\n");
            if (remaining.compareTo(BigDecimal.ZERO) > 0)
                sb.append("Осталось:   *").append(fmt(remaining)).append("*\n");
            else
                sb.append("🔴 Превышение: *").append(fmt(remaining.abs())).append("*\n");
        });

        // Subcategory breakdown
        List<Object[]> subcats = transactionRepository.sumBySubcategoryInCategory(user, categoryId, from, to);
        if (!subcats.isEmpty()) {
            sb.append("\n📂 *По подкатегориям:*\n\n");
            for (Object[] row : subcats) {
                String  sName  = (String)     row[0];
                String  sEmoji = (String)     row[1];
                BigDecimal amt = (BigDecimal) row[2];
                int pct = spent.compareTo(BigDecimal.ZERO) > 0
                        ? amt.multiply(BigDecimal.valueOf(100))
                        .divide(spent, 0, RoundingMode.HALF_UP).intValue()
                        : 0;
                sb.append(sEmoji != null ? sEmoji + " " : "• ");
                sb.append(sName).append("  *").append(fmt(amt)).append("*");
                sb.append("  _(").append(pct).append("%)_\n");
            }
        }

        // Insight
        List<Transaction> txs = transactionRepository.findExpensesByCategoryAndPeriod(user, categoryId, from, to);
        if (!txs.isEmpty()) {
            sb.append("\n_Записей: ").append(txs.size()).append("_\n");
            findTopDescription(txs).ifPresent(top ->
                    sb.append("_Чаще всего: ").append(top).append("_\n"));
        }

        bot.sendMessage(chatId, sb.toString(),
                KeyboardFactory.overviewCategory(categoryId, !subcats.isEmpty(), budgetService.getGoal(user, cat).isPresent()));
    }

    // ================================================================
    // 3. SUBCATEGORY DRILL-DOWN
    // ================================================================

    @Transactional(readOnly = true)
    public void showSubcategory(User user, long chatId, MonetkaBot bot, long subcategoryId) {
        Subcategory sub = subcategoryRepository.findById(subcategoryId).orElse(null);
        if (sub == null) { bot.sendText(chatId, "Подкатегория не найдена."); return; }

        LocalDate now  = LocalDate.now(BISHKEK);
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        List<Transaction> txs = transactionRepository.findExpensesBySubcategoryAndPeriod(user, subcategoryId, from, to);
        BigDecimal total = txs.stream().map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder();
        sb.append(sub.getEmoji() != null ? sub.getEmoji() + " " : "");
        sb.append("*").append(sub.getName()).append(" — ").append(statisticsService.currentMonthName()).append("*\n");
        sb.append("━━━━━━━━━━━━━━━━\n");
        sb.append("Всего:  *").append(fmt(total)).append("*\n");
        sb.append("Записей: ").append(txs.size()).append("\n");

        if (!txs.isEmpty()) {
            BigDecimal avg = total.divide(BigDecimal.valueOf(txs.size()), 0, RoundingMode.HALF_UP);
            sb.append("Средний чек: *").append(fmt(avg)).append("*\n");
        }

        if (!txs.isEmpty()) {
            sb.append("\n📋 *Записи:*\n\n");
            int show = Math.min(txs.size(), 10);
            for (int i = 0; i < show; i++) {
                Transaction tx = txs.get(i);
                sb.append(tx.getCreatedAt().toLocalDate().format(SHORT))
                        .append("  ").append(tx.getDescription())
                        .append("  *−").append(fmt(tx.getAmount())).append("*\n");
            }
            if (txs.size() > 10)
                sb.append("_...и ещё ").append(txs.size() - 10).append("_\n");
        }

        // find top description
        findTopDescription(txs).ifPresent(top ->
                sb.append("\n💡 *Чаще всего:* ").append(top).append("\n"));

        Long catId = sub.getCategory() != null ? sub.getCategory().getId() : null;
        bot.sendMessage(chatId, sb.toString(),
                KeyboardFactory.overviewSubcategory(subcategoryId, catId, txs));
    }

    // ================================================================
    // 4. DAYS VIEW
    // ================================================================

    @Transactional(readOnly = true)
    public void showDays(User user, long chatId, MonetkaBot bot, long categoryId) {
        Category cat = categoryRepository.findById(categoryId).orElse(null);
        if (cat == null) { bot.sendText(chatId, "Категория не найдена."); return; }

        LocalDate now  = LocalDate.now(BISHKEK);
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        List<Transaction> txs = transactionRepository.findExpensesByCategoryAndPeriod(user, categoryId, from, to);

        // Group by day
        Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
        Map<LocalDate, List<String>> descs = new LinkedHashMap<>();
        for (Transaction tx : txs) {
            LocalDate d = tx.getCreatedAt().toLocalDate();
            byDay.merge(d, tx.getAmount(), BigDecimal::add);
            descs.computeIfAbsent(d, k -> new ArrayList<>()).add(tx.getDescription());
        }

        if (byDay.isEmpty()) {
            bot.sendMessage(chatId, "В этой категории пока нет записей 🌱",
                    KeyboardFactory.backToCategory(categoryId));
            return;
        }

        // Find max for bar scaling
        BigDecimal maxDay = byDay.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);

        StringBuilder sb = new StringBuilder();
        sb.append(cat.getEmoji() != null ? cat.getEmoji() + " " : "");
        sb.append("*").append(cat.getName()).append(" по дням*\n\n");

        // Sort days ascending
        new TreeMap<>(byDay).forEach((day, amt) -> {
            int bars = maxDay.compareTo(BigDecimal.ZERO) > 0
                    ? amt.multiply(BigDecimal.valueOf(8))
                    .divide(maxDay, 0, RoundingMode.HALF_UP).intValue()
                    : 0;
            sb.append(day.format(SHORT)).append("  ");
            sb.append("█".repeat(Math.max(1, bars)));
            sb.append("  *").append(fmt(amt)).append("*");
            List<String> ds = descs.get(day);
            if (ds != null && ds.size() == 1) sb.append("  _").append(ds.get(0)).append("_");
            else if (ds != null) sb.append("  _(").append(ds.size()).append(" записи)_");
            sb.append("\n");
        });

        // Insight — busiest day of week
        Map<String, BigDecimal> byWeekday = new LinkedHashMap<>();
        byDay.forEach((day, amt) -> {
            String wd = day.getDayOfWeek().getDisplayName(
                    java.time.format.TextStyle.FULL_STANDALONE, new Locale("ru"));
            byWeekday.merge(wd, amt, BigDecimal::add);
        });
        byWeekday.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> sb.append("\n💡 Больше всего тратишь по _")
                        .append(e.getKey()).append("_\n"));

        bot.sendMessage(chatId, sb.toString(), KeyboardFactory.backToCategory(categoryId));
    }

    // ================================================================
    // 5. GOALS MANAGEMENT
    // ================================================================

    @Transactional(readOnly = true)
    public void showGoals(User user, long chatId, MonetkaBot bot) {
        List<BudgetGoal> goals = budgetService.getGoals(user);
        List<Category>   allCats = categoryRepository.findAllByIsDefaultFalseOrderByName();

        StringBuilder sb = new StringBuilder("🎯 *Мои цели на месяц*\n\n");

        if (goals.isEmpty()) {
            sb.append("_Целей пока нет._\n\n");
            sb.append("Нажми на категорию чтобы поставить цель.\n");
            sb.append("Бот подскажет реалистичную цифру на основе твоей истории 💡\n");
        } else {
            LocalDate now  = LocalDate.now(BISHKEK);
            LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
            LocalDateTime to   = from.plusMonths(1);

            for (BudgetGoal g : goals) {
                BigDecimal spent = budgetService.getCategorySpentForPeriod(user, g.getCategory(), from, to);
                int pct = g.getAmount().compareTo(BigDecimal.ZERO) > 0
                        ? spent.multiply(BigDecimal.valueOf(100))
                        .divide(g.getAmount(), 0, RoundingMode.HALF_UP).intValue()
                        : 0;
                sb.append(g.getCategory().getEmoji() != null ? g.getCategory().getEmoji() + " " : "");
                sb.append("*").append(g.getCategory().getName()).append("*  ");
                sb.append(fmt(spent)).append(" / ").append(fmt(g.getAmount())).append("\n");
                sb.append(progressBar(pct)).append("  ").append(pct).append("%\n\n");
            }
        }

        bot.sendMessage(chatId, sb.toString(), KeyboardFactory.overviewGoals(goals, allCats));
    }

    // ================================================================
    // 6. SET GOAL — step 1: show suggestion
    // ================================================================

    @Transactional
    public void startSetGoal(User user, long chatId, MonetkaBot bot, long categoryId) {
        Category cat = categoryRepository.findById(categoryId).orElse(null);
        if (cat == null) { bot.sendText(chatId, "Категория не найдена."); return; }

        BigDecimal suggested = budgetService.suggestGoal(user, cat);
        String catLabel = (cat.getEmoji() != null ? cat.getEmoji() + " " : "") + cat.getName();

        stateService.setState(user.getTelegramId(), UserState.WAITING_GOAL_AMOUNT);
        stateService.putData(user.getTelegramId(), "goal_category_id", String.valueOf(categoryId));

        StringBuilder sb = new StringBuilder();
        sb.append("🎯 *Цель для «").append(catLabel).append("»*\n\n");

        if (suggested.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("За последние 3 месяца ты тратил в среднем\n");
            sb.append("*").append(fmt(suggested.divide(BigDecimal.valueOf(1.1), 0, RoundingMode.HALF_UP))).append("* в месяц.\n\n");
            sb.append("💡 Моё предложение: *").append(fmt(suggested)).append("*\n");
            sb.append("_(чуть выше среднего — реалистично)_\n\n");
        }

        budgetService.getGoal(user, cat).ifPresent(g ->
                sb.append("Текущая цель: *").append(fmt(g.getAmount())).append("*\n\n"));

        sb.append("Введи свою цифру в сомах:");

        bot.sendMessage(chatId, sb.toString(),
                com.monetka.bot.keyboard.KeyboardFactory.cancelMenu());
    }

    // ================================================================
    // 7. DELETE GOAL
    // ================================================================

    @Transactional
    public void deleteGoal(User user, long chatId, MonetkaBot bot, long categoryId) {
        Category cat = categoryRepository.findById(categoryId).orElse(null);
        if (cat == null) return;
        budgetService.deleteGoal(user, cat);
        bot.sendMarkdown(chatId, "🗑 Цель для *" + cat.getName() + "* удалена.");
        showGoals(user, chatId, bot);
    }

    // ================================================================
    // 8. DELETE TRANSACTION
    // ================================================================

    @Transactional(readOnly = true)
    public void confirmDeleteTx(User user, long chatId, MonetkaBot bot, long txId) {
        transactionRepository.findById(txId).ifPresentOrElse(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            String catName = tx.getCategory() != null ? tx.getCategory().getName() : "—";
            bot.sendMessage(chatId,
                    "🗑 *Удалить запись?*\n\n" +
                            "📝 " + tx.getDescription() + "\n" +
                            "💸 −" + fmt(tx.getAmount()) + "\n" +
                            "🏷 " + catName + "\n" +
                            "📅 " + tx.getCreatedAt().toLocalDate().format(DATE_FMT),
                    KeyboardFactory.confirmDeleteTx(txId));
        }, () -> bot.sendText(chatId, "Запись не найдена."));
    }

    @Transactional
    public void doDeleteTx(User user, long chatId, MonetkaBot bot, long txId) {
        transactionRepository.findById(txId).ifPresentOrElse(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) {
                bot.sendText(chatId, "Нет доступа.");
                return;
            }
            // Restore balance
            user.setBalance(user.getBalance().add(tx.getAmount()));
            transactionRepository.delete(tx);
            bot.sendMarkdown(chatId, "✅ Запись удалена. Баланс восстановлен.");
        }, () -> bot.sendText(chatId, "Запись не найдена."));
    }

    // ================================================================
    // Handle WAITING_GOAL_AMOUNT text input
    // ================================================================

    @Transactional
    public boolean handleGoalAmountInput(User user, String text, long chatId, MonetkaBot bot) {
        String catIdStr = stateService.getData(user.getTelegramId(), "goal_category_id");
        if (catIdStr == null) { stateService.reset(user.getTelegramId()); return false; }

        BigDecimal amount;
        try {
            amount = new BigDecimal(text.replace(",", ".").replace(" ", ""));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            bot.sendMessage(chatId, "Введи число, например: *15000*",
                    KeyboardFactory.cancelMenu());
            return true;
        }

        Category cat = categoryRepository.findById(Long.parseLong(catIdStr)).orElse(null);
        if (cat == null) { stateService.reset(user.getTelegramId()); return false; }

        budgetService.setGoal(user, cat, amount);
        stateService.reset(user.getTelegramId());

        String catLabel = (cat.getEmoji() != null ? cat.getEmoji() + " " : "") + cat.getName();
        bot.sendMessage(chatId,
                "✅ *Цель установлена!*\n\n" +
                        catLabel + "  →  *" + fmt(amount) + " / мес*\n\n" +
                        "Буду напоминать при 80%, 90% и 100% 💡",
                KeyboardFactory.mainMenu());
        return true;
    }

    // ================================================================
    // 9. EDIT TRANSACTION
    // ================================================================

    @Transactional(readOnly = true)
    public void showTxDetail(User user, long chatId, MonetkaBot bot, long txId) {
        transactionRepository.findById(txId).ifPresentOrElse(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            String catName = tx.getCategory() != null ? tx.getCategory().getDisplayName() : "—";
            bot.sendMessage(chatId,
                    "📝 *" + tx.getDescription() + "*\n\n" +
                            "💸 −" + fmt(tx.getAmount()) + "\n" +
                            "🏷 " + catName + "\n" +
                            "📅 " + tx.getCreatedAt().toLocalDate().format(DATE_FMT) + "\n\n" +
                            "Что хочешь сделать?",
                    KeyboardFactory.editTxOptions(txId));
        }, () -> bot.sendText(chatId, "Запись не найдена."));
    }

    @Transactional
    public void startEditAmount(User user, long chatId, MonetkaBot bot, long txId) {
        transactionRepository.findById(txId).ifPresentOrElse(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            stateService.setState(user.getTelegramId(), UserState.WAITING_EDIT_AMOUNT);
            stateService.putData(user.getTelegramId(), "edit_tx_id", String.valueOf(txId));
            bot.sendMessage(chatId,
                    "💸 *Изменить сумму*\n\n" +
                            "Текущая: *" + fmt(tx.getAmount()) + "*\n\n" +
                            "Введи новую сумму:",
                    KeyboardFactory.cancelMenu());
        }, () -> bot.sendText(chatId, "Запись не найдена."));
    }

    @Transactional
    public void startEditDesc(User user, long chatId, MonetkaBot bot, long txId) {
        transactionRepository.findById(txId).ifPresentOrElse(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            stateService.setState(user.getTelegramId(), UserState.WAITING_EDIT_DESCRIPTION);
            stateService.putData(user.getTelegramId(), "edit_tx_id", String.valueOf(txId));
            bot.sendMessage(chatId,
                    "📝 *Изменить описание*\n\n" +
                            "Текущее: *" + tx.getDescription() + "*\n\n" +
                            "Введи новое описание:",
                    KeyboardFactory.cancelMenu());
        }, () -> bot.sendText(chatId, "Запись не найдена."));
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
            bot.sendMessage(chatId, "Введи число, например: *1500*", KeyboardFactory.cancelMenu());
            return true;
        }

        transactionRepository.findById(Long.parseLong(txIdStr)).ifPresent(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            BigDecimal diff = newAmount.subtract(tx.getAmount());
            // Update user balance: if expense, adjust by diff
            if (tx.getType().name().equals("EXPENSE")) {
                user.setBalance(user.getBalance().subtract(diff));
            } else {
                user.setBalance(user.getBalance().add(diff));
            }
            tx.setAmount(newAmount);
            transactionRepository.save(tx);
            stateService.reset(user.getTelegramId());
            bot.sendMessage(chatId,
                    "✅ *Сумма обновлена!*\n\n" +
                            "📝 " + tx.getDescription() + "\n" +
                            "💸 −" + fmt(newAmount) + "\n" +
                            "💳 Баланс: *" + fmt(user.getBalance()) + "*",
                    KeyboardFactory.mainMenu());
        });
        return true;
    }

    @Transactional
    public boolean handleEditDescInput(User user, String text, long chatId, MonetkaBot bot) {
        String txIdStr = stateService.getData(user.getTelegramId(), "edit_tx_id");
        if (txIdStr == null) { stateService.reset(user.getTelegramId()); return false; }
        if (text.isBlank()) {
            bot.sendMessage(chatId, "Описание не может быть пустым 🙏", KeyboardFactory.cancelMenu());
            return true;
        }

        transactionRepository.findById(Long.parseLong(txIdStr)).ifPresent(tx -> {
            if (!tx.getUser().getId().equals(user.getId())) return;
            tx.setDescription(text.trim());
            transactionRepository.save(tx);
            stateService.reset(user.getTelegramId());
            bot.sendMessage(chatId,
                    "✅ *Описание обновлено!*\n\n" +
                            "📝 " + text.trim() + "\n" +
                            "💸 −" + fmt(tx.getAmount()),
                    KeyboardFactory.mainMenu());
        });
        return true;
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String progressBar(int percent) {
        int filled = Math.min(10, percent / 10);
        int empty  = 10 - filled;
        String bar = "█".repeat(filled) + "░".repeat(empty);
        return percent >= 100 ? "🔴 " + bar : (percent >= 80 ? "🟡 " + bar : "✅ " + bar);
    }

    private String goalBar(int percent) {
        if (percent >= 100) return "🔴";
        if (percent >= 80)  return "🟡";
        return "✅";
    }

    private String extractName(String label) {
        // "🍕 Еда" → "Еда"
        return label.replaceAll("^[\\p{So}\\p{Sm}\\s]+", "").trim();
    }

    private Optional<String> findTopDescription(List<Transaction> txs) {
        Map<String, Integer> freq = new HashMap<>();
        for (Transaction tx : txs) {
            if (tx.getDescription() != null)
                freq.merge(tx.getDescription().toLowerCase(), 1, Integer::sum);
        }
        return freq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > 1)
                .map(e -> e.getKey() + " (" + e.getValue() + " раз)");
    }

    private long parseId(String action) {
        return Long.parseLong(action.split(":")[1]);
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private String fmt(BigDecimal v) { return String.format("%,.0f сом", v); }
}