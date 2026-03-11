package com.monetka.bot.handler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.Debt;
import com.monetka.model.Subscription;
import com.monetka.model.User;
import com.monetka.model.enums.UserState;
import com.monetka.model.enums.UserStatus;
import com.monetka.service.*;
import com.monetka.service.BudgetService;
import com.monetka.service.PaydayService;
import com.monetka.insight.InsightEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Component
public class MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private static final String CANCEL_BUTTON = "❌ Отменить действие";
    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Random RND = new Random();

    private final UserService              userService;
    private final UserStateService         stateService;
    private final TransactionService       transactionService;
    private final SubscriptionService      subscriptionService;
    private final ReportService            reportService;
    private final FinancialTipsService     tipsService;
    private final CategoryDetectionService detectionService;

    private final StatisticsService       statisticsService;
    private final OverviewHandler  overviewHandler;
    private final com.monetka.admin.AdminHandler adminHandler;
    private final BudgetService    budgetService;
    private final PaydayService    paydayService;
    private final InsightEngine    insightEngine;
    private final OnboardingService onboardingService;
    private final DebtService       debtService;

    public MessageHandler(UserService userService, UserStateService stateService,
                          TransactionService transactionService, SubscriptionService subscriptionService,
                          ReportService reportService, FinancialTipsService tipsService,
                          CategoryDetectionService detectionService,
                          StatisticsService statisticsService,
                          OverviewHandler overviewHandler,
                          com.monetka.admin.AdminHandler adminHandler,
                          BudgetService budgetService,
                          PaydayService paydayService,
                          InsightEngine insightEngine,
                          OnboardingService onboardingService,
                          DebtService debtService) {
        this.userService         = userService;
        this.stateService        = stateService;
        this.transactionService  = transactionService;
        this.subscriptionService = subscriptionService;
        this.reportService       = reportService;
        this.tipsService         = tipsService;
        this.detectionService    = detectionService;
        this.statisticsService   = statisticsService;
        this.overviewHandler     = overviewHandler;
        this.adminHandler        = adminHandler;
        this.budgetService       = budgetService;
        this.paydayService       = paydayService;
        this.insightEngine       = insightEngine;
        this.onboardingService   = onboardingService;
        this.debtService         = debtService;
    }

    public void handle(Message message, MonetkaBot bot) {
        long   chatId     = message.getChatId();
        long   telegramId = message.getFrom().getId();
        String text       = message.getText();

        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            bot.sendText(chatId, "Напиши /start чтобы начать 👋");
            return;
        }
        User user = userOpt.get();

        if (user.getStatus() == UserStatus.BLOCKED) {
            bot.sendText(chatId, "🚫 Твой доступ заблокирован администратором.");
            return;
        }
        if (user.getStatus() == UserStatus.PENDING) {
            bot.sendText(chatId, "⏳ Заявка на рассмотрении, подожди немного...");
            return;
        }

        if (CANCEL_BUTTON.equals(text)) {
            stateService.reset(telegramId);
            bot.sendMessage(chatId, "Ладно, отменяю 👌 Что делаем дальше?", KeyboardFactory.mainMenu());
            return;
        }

        UserState state = stateService.getState(telegramId);
        switch (state) {
            case WAITING_EXPENSE        -> { handleExpenseInput(user, text, chatId, telegramId, bot);  return; }
            case WAITING_INCOME         -> { handleIncomeInput(user, text, chatId, telegramId, bot);   return; }
            case WAITING_SUB_NAME       -> { handleSubName(text, chatId, telegramId, bot);             return; }
            case WAITING_SUB_AMOUNT     -> { handleSubAmount(text, chatId, telegramId, bot);           return; }
            case WAITING_SUB_START_DATE -> { handleSubStartDate(text, chatId, telegramId, bot);        return; }
            case WAITING_SUB_END_DATE   -> { handleSubEndDate(user, text, chatId, telegramId, bot);    return; }
            case WAITING_GOAL_AMOUNT    -> { if (overviewHandler.handleGoalAmountInput(user, text, chatId, bot)) return; }
            case WAITING_EDIT_AMOUNT      -> { if (overviewHandler.handleEditAmountInput(user, text, chatId, bot)) return; }
            case WAITING_EDIT_DESCRIPTION -> { if (overviewHandler.handleEditDescInput(user, text, chatId, bot)) return; }
            case WAITING_DEBT_NAME    -> { handleDebtName(text, chatId, telegramId, bot);       return; }
            case WAITING_DEBT_TRIGGER -> { handleDebtTrigger(text, chatId, telegramId, bot);    return; }
            case WAITING_DEBT_TOTAL   -> { handleDebtTotal(text, chatId, telegramId, bot);      return; }
            case WAITING_DEBT_MONTHLY -> { handleDebtMonthly(text, chatId, telegramId, bot);    return; }
            case WAITING_DEBT_PAID    -> { handleDebtPaid(user, text, chatId, telegramId, bot); return; }
            case WAITING_INITIAL_BALANCE -> {
                handleInitialBalance(user, text, chatId, telegramId, bot);
                return;
            }
            case WAITING_BROADCAST_MESSAGE -> {
                adminHandler.handleBroadcastInput(chatId, telegramId, text, bot);
                return;
            }
            default -> {}
        }

        switch (text) {
            case "💸 Расход"  -> startExpense(chatId, telegramId, bot);
            case "💰 Доход"   -> startIncome(chatId, telegramId, bot);
            case "📅 Сегодня" -> bot.sendMarkdown(chatId, reportService.buildTodayStats(user), KeyboardFactory.periodPicker());
            case "📆 Неделя"  -> {
                org.telegram.telegrambots.meta.api.methods.send.SendMessage loadMsg =
                        new org.telegram.telegrambots.meta.api.methods.send.SendMessage();
                loadMsg.setChatId(chatId);
                loadMsg.setText("⏳ _Анализирую неделю..._");
                loadMsg.enableMarkdown(true);
                int weekLoadId = -1;
                try { weekLoadId = bot.execute(loadMsg).getMessageId(); } catch (Exception ignored) {}
                String weekText = reportService.buildWeekStats(user);
                if (weekLoadId > 0) bot.editMessage(chatId, weekLoadId, weekText, KeyboardFactory.periodPicker());
                else bot.sendMarkdown(chatId, weekText, KeyboardFactory.periodPicker());
            }
            case "🗓 Месяц"   -> {
                org.telegram.telegrambots.meta.api.methods.send.SendMessage loadMsg =
                        new org.telegram.telegrambots.meta.api.methods.send.SendMessage();
                loadMsg.setChatId(chatId);
                loadMsg.setText("⏳ _Считаю цифры и думаю над советом..._");
                loadMsg.enableMarkdown(true);
                int monthLoadId = -1;
                try { monthLoadId = bot.execute(loadMsg).getMessageId(); } catch (Exception ignored) {}
                String monthText = reportService.buildMonthStats(user);
                if (monthLoadId > 0) bot.editMessage(chatId, monthLoadId, monthText, KeyboardFactory.periodPicker());
                else bot.sendMarkdown(chatId, monthText, KeyboardFactory.periodPicker());
            }
            case "🎯 Лимиты"  -> overviewHandler.showGoals(user, chatId, bot);
            case "📊 Обзор"   -> overviewHandler.showMain(user, chatId, bot);
            case "❓ Помощь"   -> sendHelp(chatId, bot);
            default -> {
                // Если во время онбординга пользователь пишет расход — обрабатываем сразу
                ParseResult tryParse = parse(text);
                if (tryParse != null) {
                    stateService.setState(telegramId, UserState.WAITING_EXPENSE);
                    handleExpenseInput(user, text, chatId, telegramId, bot);
                } else {
                    bot.sendMessage(chatId,
                            pick("Хм, не понял 🤔 Используй кнопки или команды: /balance /stats /help",
                                    "Что-то не то написал, используй кнопки 👇",
                                    "Не знаю такой команды 😅 Попробуй /help"),
                            KeyboardFactory.mainMenu());
                }
            }
        }
    }

    // ================================================================
    // Expense
    // ================================================================

    private void startExpense(long chatId, long telegramId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_EXPENSE);
        bot.sendMessage(chatId,
                pick("Окей, записываю расход 📝\n\nФормат: *название сумма*\nНапример: `шаурма 300`",
                        "Сколько потратил? 💸\n\nПиши в формате: *название сумма*\nНапример: `кофе 150`",
                        "Записываем трату 🖊\n\nФормат: *название сумма*\nНапример: `такси 500`"),
                KeyboardFactory.cancelMenu());
    }

    private void handleExpenseInput(User user, String text, long chatId,
                                    long telegramId, MonetkaBot bot) {
        ParseResult p = parse(text);
        if (p == null) {
            bot.sendMessage(chatId,
                    "Не понял формат 🤔 Пиши так: *название сумма*\nНапример: `шаурма 300`",
                    KeyboardFactory.cancelMenu());
            return;
        }

        CategoryDetectionService.DetectionResult detection =
                detectionService.detectCategory(p.description, user.getTelegramId());

        stateService.putData(telegramId, "expense_desc",   p.description);
        stateService.putData(telegramId, "expense_amount", p.amount.toPlainString());

        // use shouldAutoSave() — learned keywords always auto-save
        if (detection.shouldAutoSave()) {
            saveExpense(user, p, detection, chatId, telegramId, bot);
        } else if (detection.hasSuggestion()) {
            // Medium confidence — ask user to confirm suggestion
            stateService.setState(telegramId, UserState.WAITING_CATEGORY_CHOICE);
            long catId = detection.getCategory().getId();
            Long subId = detection.getSubcategory() != null ? detection.getSubcategory().getId() : null;
            bot.sendMarkdown(chatId,
                    "🤔 *" + p.description + "*\n\nВозможно ты имел в виду " + detection.suggestionLabel() + "?",
                    KeyboardFactory.suggestCategory(detection.suggestionLabel(), catId, subId));
        } else {
            stateService.setState(telegramId, UserState.WAITING_CATEGORY_CHOICE);
            bot.sendMarkdown(chatId,
                    "🤔 Не знаю куда отнести *" + p.description + "*\n\nВыбери категорию — запомню:",
                    KeyboardFactory.categoryChoice(detectionService.getAllCategories()));
        }
    }

    private void saveExpense(User user, ParseResult p,
                             CategoryDetectionService.DetectionResult detection,
                             long chatId, long telegramId, MonetkaBot bot) {
        com.monetka.model.Transaction tx = transactionService.addExpense(user, p.amount, p.description);
        stateService.reset(telegramId);

        // Долговой триггер — проверяем SAFE: любая ошибка не роняет расход
        String debtNote = "";
        try {
            java.util.Optional<Debt> debtHit = debtService.applyPayment(user, p.description, p.amount);
            if (debtHit.isPresent()) {
                Debt d = debtHit.get();
                if (d.isClosed()) {
                    debtNote = "\n\n\uD83C\uDF89 *" + d.getName() + " \u2014 ЗАКРЫТ!*\n"
                            + "_+" + fmt(d.getMonthlyPayment()) + "/мес теперь свободно!_";
                } else {
                    debtNote = "\n\n\uD83D\uDCB3 *" + d.getName() + "*\n   "
                            + d.progressBar() + "\n   Осталось: *" + fmt(d.getRemaining()) + "*";
                }
            }
        } catch (Exception e) {
            log.warn("Debt trigger check failed: {}", e.getMessage());
        }

        String cat = detection.display();
        String learnedNote = detection.isFromLearned() ? "\n🧠 _Узнал по памяти_" : "";
        String confNote = (!detection.isFromLearned() && detection.getConfidence() < 1.0)
                ? " _(~" + Math.round(detection.getConfidence() * 100) + "%)_" : "";

        String[] reactions = {"✅", "💾", "📌"};
        String reaction = reactions[RND.nextInt(reactions.length)];

        // Smart pace line — compare today vs average daily this month
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bishkek"));
        java.time.LocalDateTime tFrom = today.atStartOfDay();
        java.time.LocalDateTime tTo   = today.plusDays(1).atStartOfDay();
        java.math.BigDecimal todayTotal = statisticsService.getMonthExpensesForPeriod(user, tFrom, tTo);

        java.time.LocalDateTime mFrom = today.withDayOfMonth(1).atStartOfDay();
        java.math.BigDecimal monthTotal = statisticsService.getMonthExpensesForPeriod(user, mFrom, tTo);
        int dayOfMonth = today.getDayOfMonth();
        int daysInMonth = today.lengthOfMonth();

        String paceLine = buildPaceLine(todayTotal, monthTotal, dayOfMonth, daysInMonth);

        bot.sendMessage(chatId,
                reaction + " *Записал!*\n\n" +
                        "\uD83D\uDCDD " + com.monetka.bot.MonetkaBot.esc(p.description) + "\n" +
                        "\uD83D\uDCB8 \u2212" + fmt(p.amount) + "\n" +
                        "\uD83C\uDFF7 " + cat + confNote + learnedNote + "\n" +
                        "\uD83D\uDCB3 Баланс: *" + fmt(user.getBalance()) + "*" +
                        paceLine + debtNote,
                KeyboardFactory.mainMenu());

        // Budget goal alert — send after main confirmation if threshold crossed
        if (tx.getCategory() != null) {
            budgetService.checkAfterExpense(user, tx.getCategory())
                    .ifPresent(alert -> bot.sendMarkdown(chatId, alert));
        }

        // Insight engine — check triggers
        insightEngine.onTransaction(user, tx, bot);
    }

    // ================================================================
    // Income
    // ================================================================

    private void startIncome(long chatId, long telegramId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_INCOME);
        bot.sendMessage(chatId,
                pick("Отлично, записываю доход 💰\n\nФормат: *название сумма*\nНапример: `зарплата 50000`",
                        "Пришли деньги? Записываем! 🤑\n\nПиши: *название сумма*\nНапример: `фриланс 15000`",
                        "Доход — это хорошо! 🚀\n\nФормат: *название сумма*"),
                KeyboardFactory.cancelMenu());
    }

    private void handleIncomeInput(User user, String text, long chatId,
                                   long telegramId, MonetkaBot bot) {
        ParseResult p = parse(text);
        if (p == null) {
            bot.sendMessage(chatId,
                    "Не понял 🤔 Пиши так: *название сумма*\nНапример: `зарплата 50000`",
                    KeyboardFactory.cancelMenu());
            return;
        }
        transactionService.addIncome(user, p.amount, p.description);
        paydayService.onIncome(user, p.amount);
        stateService.reset(telegramId);

        // Show updated daily budget based on real days left in month
        String cycleHint = paydayService.getCycleStatus(user)
                .map(s -> "\n\n\uD83D\uDCC5 _\u0411\u044e\u0434\u0436\u0435\u0442: " +
                        String.format("%,.0f \u0441\u043e\u043c/\u0434\u0435\u043d\u044c" , s.dailyBudget) +
                        " (\u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c " + s.daysLeft + " \u0434\u043d.)_")
                .orElse("");

        bot.sendMessage(chatId,
                pick("🎉 *Доход записан!*", "💰 *Зафиксировал!*", "✅ *Добавлено!*") + "\n\n" +
                        "📝 " + com.monetka.bot.MonetkaBot.esc(p.description) + "\n" +
                        "💰 +" + fmt(p.amount) + "\n" +
                        "💳 Баланс: *" + fmt(user.getBalance()) + "*" + cycleHint,
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // Онбординг — начальный баланс
    // ================================================================

    private void handleInitialBalance(User user, String text, long chatId, long telegramId, MonetkaBot bot) {
        try {
            BigDecimal amount = new BigDecimal(text.trim().replace(",", ".").replace(" ", ""));
            if (amount.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
            transactionService.addIncome(user, amount, "Начальный баланс");
            stateService.reset(telegramId);
            onboardingService.sendFinish(chatId, bot);
        } catch (NumberFormatException e) {
            bot.sendMessage(chatId,
                    "Не понял число \uD83E\uDD14 Напиши просто сумму, например: `32000`",
                    KeyboardFactory.onboardingBalanceSkip());
        }
    }

    // ================================================================
    // Subscription wizard
    // ================================================================

    private void handleSubName(String text, long chatId, long telegramId, MonetkaBot bot) {
        stateService.putData(telegramId, "sub_name", text.trim());
        stateService.setState(telegramId, UserState.WAITING_SUB_AMOUNT);
        bot.sendMessage(chatId,
                "Понял, *" + text.trim() + "* 👍\n\nТеперь сумма в месяц (например: `1500`):",
                KeyboardFactory.cancelMenu());
    }

    private void handleSubAmount(String text, long chatId, long telegramId, MonetkaBot bot) {
        try {
            BigDecimal amount = new BigDecimal(text.trim().replace(",", "."));
            stateService.putData(telegramId, "sub_amount", amount.toPlainString());
            stateService.setState(telegramId, UserState.WAITING_SUB_START_DATE);
            bot.sendMessage(chatId,
                    "Отлично! *" + fmt(amount) + "/мес* — записал 💾\n\nС какой даты? Формат: *ДД.ММ.ГГГГ*",
                    KeyboardFactory.cancelWithSkip("Сегодня"));
        } catch (NumberFormatException e) {
            bot.sendMessage(chatId, "Это не число 😅 Введи сумму, например: `1500`", KeyboardFactory.cancelMenu());
        }
    }

    private void handleSubStartDate(String text, long chatId, long telegramId, MonetkaBot bot) {
        LocalDate startDate;
        if (text.equalsIgnoreCase("сегодня") || text.startsWith("⏭")) {
            startDate = LocalDate.now();
        } else {
            startDate = parseDate(text);
            if (startDate == null) {
                bot.sendMessage(chatId,
                        "Неверный формат 🤔 Пиши *ДД.ММ.ГГГГ*\nНапример: `" + LocalDate.now().format(D_FMT) + "`",
                        KeyboardFactory.cancelWithSkip("Сегодня"));
                return;
            }
        }
        stateService.putData(telegramId, "sub_start", startDate.toString());
        stateService.setState(telegramId, UserState.WAITING_SUB_END_DATE);
        bot.sendMessage(chatId,
                "📅 Начало: *" + startDate.format(D_FMT) + "* ✅\n\nДо какой даты? Или бессрочно?",
                KeyboardFactory.cancelWithSkip("Бессрочно"));
    }

    private void handleSubEndDate(User user, String text, long chatId,
                                  long telegramId, MonetkaBot bot) {
        LocalDate endDate = null;
        if (!text.equalsIgnoreCase("бессрочно") && !text.startsWith("⏭")) {
            endDate = parseDate(text);
            if (endDate == null) {
                bot.sendMessage(chatId,
                        "Неверный формат 🤔 Пиши *ДД.ММ.ГГГГ* или нажми Бессрочно",
                        KeyboardFactory.cancelWithSkip("Бессрочно"));
                return;
            }
        }

        String     name   = stateService.getData(telegramId, "sub_name");
        BigDecimal amount = new BigDecimal(stateService.getData(telegramId, "sub_amount"));
        LocalDate  start  = LocalDate.parse(stateService.getData(telegramId, "sub_start"));

        Subscription sub = subscriptionService.create(user, name, amount, start, endDate);
        stateService.reset(telegramId);

        String tip = tipsService.tipForSubscription(name, amount);

        StringBuilder reply = new StringBuilder("🔔 *Подписка добавлена!*\n\n");
        reply.append("📝 ").append(sub.getName()).append("\n");
        reply.append("💸 ").append(fmt(sub.getAmount())).append("/мес\n");
        reply.append("📅 С ").append(start.format(D_FMT));
        if (endDate != null) reply.append(" до ").append(endDate.format(D_FMT));
        else reply.append(" — бессрочно ♾");
        reply.append("\n\n").append(tip);

        bot.sendMessage(chatId, reply.toString(), KeyboardFactory.mainMenu());
    }

    // ================================================================
    // Helpers
    // ================================================================

    /**
     * Одна умная строка после каждой траты.
     * Сравнивает сегодня с обычным днём и показывает прогноз.
     */
    private String buildPaceLine(java.math.BigDecimal todayTotal,
                                 java.math.BigDecimal monthTotal,
                                 int dayOfMonth, int daysInMonth) {
        if (todayTotal == null || todayTotal.compareTo(java.math.BigDecimal.ZERO) == 0)
            return "";
        if (monthTotal == null || monthTotal.compareTo(java.math.BigDecimal.ZERO) == 0)
            return "";

        // Средний день за месяц
        java.math.BigDecimal avgDay = monthTotal.divide(
                java.math.BigDecimal.valueOf(dayOfMonth), 0, java.math.RoundingMode.HALF_UP);

        // Прогноз на месяц по среднему темпу
        java.math.BigDecimal projected = avgDay.multiply(java.math.BigDecimal.valueOf(daysInMonth));
        String projStr = String.format("%,.0f", projected);

        // Сравниваем сегодня с обычным днём
        double ratio = todayTotal.divide(avgDay.compareTo(java.math.BigDecimal.ZERO) > 0
                ? avgDay : java.math.BigDecimal.ONE, 2, java.math.RoundingMode.HALF_UP).doubleValue();

        String line;
        if (dayOfMonth == 1 || avgDay.compareTo(java.math.BigDecimal.valueOf(50)) < 0) {
            // Первый день месяца или слишком мало данных — просто прогноз
            java.math.BigDecimal simpleProjected = todayTotal.multiply(java.math.BigDecimal.valueOf(daysInMonth));
            line = "\n📊 Если каждый день так — *~" + String.format("%,.0f", simpleProjected) + "* за месяц";
        } else if (ratio <= 0.5) {
            line = "\n🟢 Экономный день — месяц идёт на *~" + projStr + "*";
        } else if (ratio <= 1.3) {
            line = "\n🟢 Норм темп — если так весь месяц, потратишь *~" + projStr + "*";
        } else if (ratio <= 2.0) {
            line = "\n🟡 Горячий день — месяц пока на *~" + projStr + "*";
        } else {
            line = "\n🔴 Много сегодня — месяц пока на *~" + projStr + "*";
        }
        return line;
    }

    private ParseResult parse(String text) {
        if (text == null || text.isBlank()) return null;
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length < 2) return null;
        try {
            BigDecimal amount = new BigDecimal(tokens[tokens.length - 1].replace(",", "."));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) return null;
            String desc = String.join(" ", Arrays.copyOf(tokens, tokens.length - 1)).trim();
            return new ParseResult(desc.isBlank() ? "Без описания" : desc, amount);
        } catch (NumberFormatException ignored) {}
        try {
            BigDecimal amount = new BigDecimal(tokens[0].replace(",", "."));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) return null;
            String desc = String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)).trim();
            return new ParseResult(desc.isBlank() ? "Без описания" : desc, amount);
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private LocalDate parseDate(String text) {
        try { return LocalDate.parse(text.trim(), D_FMT); } catch (DateTimeParseException e) { return null; }
    }

    private void sendHelp(long chatId, MonetkaBot bot) {
        String msg =
                "❓ *Как пользоваться Monetka*\n\n" +
                        "💸 *Расход* — записать трату\n" +
                        "_Или просто напиши: шаурма 300_\n\n" +
                        "💰 *Доход* — записать поступление денег\n" +
                        "_Это запускает цикл до зарплаты_\n\n" +
                        "📊 *Обзор* — все расходы месяца по категориям, прогноз\n\n" +
                        "🎯 *Лимиты* — установи максимум для категории\n" +
                        "_Бот предупредит когда будешь близко_\n\n" +
                        "✏️ *Редактирование* — открой категорию в Обзоре\n" +
                        "_Можно изменить сумму, описание или удалить трату_\n\n" +
                        "📅 *Отчёты* — автоматически каждый день в 23:55,\n" +
                        "_понедельник 10:00 и 1-го числа_\n\n" +
                        "/stats — подробная статистика\n" +
                        "/balance — текущий баланс\n" +
                        "/день — статус цикла до зарплаты";
        bot.sendMessage(chatId, msg, KeyboardFactory.mainMenu());
    }

    // ============================================================
    // ДОЛГИ — диалог создания (5 шагов)
    // ============================================================

    private void handleDebtName(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (text.isBlank()) { bot.sendText(chatId, "Введи название долга:"); return; }
        stateService.putData(telegramId, "debt_name", text.trim());
        stateService.setState(telegramId, UserState.WAITING_DEBT_TRIGGER);
        bot.sendMarkdown(chatId,
                "Придумай слово-триггер \u2014 *одно слово*, которое будешь писать при каждом платеже.\n\n" +
                        "_Например: `\u0437\u0430\u0440\u043f\u043b\u0430\u0442\u043d\u044b\u0439`, `\u0442\u0435\u043b\u0435\u0444\u043e\u043d`, `\u0430\u0437\u0438\u0437\u0443`_\n\n" +
                        "Потом просто пишешь: `\u0437\u0430\u0440\u043f\u043b\u0430\u0442\u043d\u044b\u0439 13000` \u2014 и бот сам найдёт этот долг.");
    }

    private void handleDebtTrigger(String text, long chatId, long telegramId, MonetkaBot bot) {
        String trigger = text.trim().toLowerCase().split("\\s+")[0];
        if (trigger.isBlank()) { bot.sendText(chatId, "Введи одно слово-триггер:"); return; }
        stateService.putData(telegramId, "debt_trigger", trigger);
        stateService.setState(telegramId, UserState.WAITING_DEBT_TOTAL);
        bot.sendMarkdown(chatId, "Сколько всего должен? _(сом)_");
    }

    private void handleDebtTotal(String text, long chatId, long telegramId, MonetkaBot bot) {
        try {
            BigDecimal total = new BigDecimal(text.trim().replace(",", ".").replace(" ", ""));
            if (total.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            stateService.putData(telegramId, "debt_total", total.toPlainString());
            stateService.setState(telegramId, UserState.WAITING_DEBT_MONTHLY);
            bot.sendMarkdown(chatId, "Сколько платишь в месяц? _(сом)_");
        } catch (NumberFormatException e) {
            bot.sendText(chatId, "Введи сумму числом, например: 50000");
        }
    }

    private void handleDebtMonthly(String text, long chatId, long telegramId, MonetkaBot bot) {
        try {
            BigDecimal monthly = new BigDecimal(text.trim().replace(",", ".").replace(" ", ""));
            if (monthly.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            stateService.putData(telegramId, "debt_monthly", monthly.toPlainString());
            stateService.setState(telegramId, UserState.WAITING_DEBT_PAID);
            bot.sendMarkdown(chatId, "Уже выплатил что-то? _(сом)_\n\n_Если нет \u2014 напиши `0`_");
        } catch (NumberFormatException e) {
            bot.sendText(chatId, "Введи сумму числом, например: 13000");
        }
    }

    private void handleDebtPaid(User user, String text, long chatId, long telegramId, MonetkaBot bot) {
        try {
            BigDecimal paid = new BigDecimal(text.trim().replace(",", ".").replace(" ", ""));
            if (paid.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();

            String name    = stateService.getData(telegramId, "debt_name");
            String trigger = stateService.getData(telegramId, "debt_trigger");
            BigDecimal total   = new BigDecimal(stateService.getData(telegramId, "debt_total"));
            BigDecimal monthly = new BigDecimal(stateService.getData(telegramId, "debt_monthly"));

            if (name == null || trigger == null) {
                bot.sendText(chatId, "Что-то пошло не так. Начни заново: /adddebt");
                stateService.reset(telegramId);
                return;
            }

            com.monetka.model.Debt d = debtService.create(user, name, trigger, total, monthly, paid);
            stateService.reset(telegramId);

            int months = d.monthsLeft();
            String closeDate = months > 0
                    ? java.time.LocalDate.now().plusMonths(months)
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", new java.util.Locale("ru")))
                    : "скоро";

            bot.sendMarkdown(chatId,
                    "\u2705 *" + name + " \u2014 добавлен!*\n\n" +
                            "Осталось: *" + fmt(d.getRemaining()) + "*\n" +
                            "Закроется: *~" + closeDate + "* (~" + months + " мес)\n\n" +
                            d.progressBar() + "\n\n" +
                            "_Теперь просто пиши `" + trigger + " " + fmt(monthly).replace(" сом","") + "` \u2014 бот сам найдёт этот долг_\n\n" +
                            "/debt \u2014 посмотреть все долги");
        } catch (NumberFormatException e) {
            bot.sendText(chatId, "Введи сумму числом, например: 26000 или 0");
        } catch (Exception e) {
            log.error("Error creating debt for user {}: {}", user.getTelegramId(), e.getMessage(), e);
            bot.sendText(chatId, "Ошибка при сохранении долга. Попробуй ещё раз: /adddebt");
            stateService.reset(telegramId);
        }
    }

    private String fmt(BigDecimal amount) { return String.format("%,.0f сом", amount); }

    /** Pick a random string from options for variety */
    @SafeVarargs
    private static <T> T pick(T... options) { return options[RND.nextInt(options.length)]; }

    private static final class ParseResult {
        final String description;
        final BigDecimal amount;
        ParseResult(String d, BigDecimal a) { description = d; amount = a; }
    }
}