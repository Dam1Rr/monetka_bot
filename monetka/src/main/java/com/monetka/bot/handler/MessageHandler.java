package com.monetka.bot.handler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
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

    private final OverviewHandler  overviewHandler;
    private final BudgetService    budgetService;
    private final PaydayService    paydayService;
    private final InsightEngine    insightEngine;

    public MessageHandler(UserService userService, UserStateService stateService,
                          TransactionService transactionService, SubscriptionService subscriptionService,
                          ReportService reportService, FinancialTipsService tipsService,
                          CategoryDetectionService detectionService,
                          OverviewHandler overviewHandler,
                          BudgetService budgetService,
                          PaydayService paydayService,
                          InsightEngine insightEngine) {
        this.userService         = userService;
        this.stateService        = stateService;
        this.transactionService  = transactionService;
        this.subscriptionService = subscriptionService;
        this.reportService       = reportService;
        this.tipsService         = tipsService;
        this.detectionService    = detectionService;
        this.overviewHandler     = overviewHandler;
        this.budgetService       = budgetService;
        this.paydayService       = paydayService;
        this.insightEngine       = insightEngine;
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
            default -> {}
        }

        switch (text) {
            case "💸 Расход" -> startExpense(chatId, telegramId, bot);
            case "💰 Доход"  -> startIncome(chatId, telegramId, bot);
            case "📊 Обзор"  -> overviewHandler.showMain(user, chatId, bot);
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

        // BUG FIX: use shouldAutoSave() — learned keywords always auto-save
        //          even if mapped to default "Прочее" category
        if (detection.shouldAutoSave()) {
            saveExpense(user, p, detection, chatId, telegramId, bot);
        } else {
            stateService.setState(telegramId, UserState.WAITING_CATEGORY_CHOICE);
            bot.sendMessage(chatId,
                    "🤔 Хм, не знаю куда отнести *" + p.description + "*\n\nВыбери категорию — запомню на будущее:",
                    KeyboardFactory.categoryChoice(detectionService.getAllCategories()));
        }
    }

    private void saveExpense(User user, ParseResult p,
                             CategoryDetectionService.DetectionResult detection,
                             long chatId, long telegramId, MonetkaBot bot) {
        com.monetka.model.Transaction tx = transactionService.addExpense(user, p.amount, p.description);
        stateService.reset(telegramId);

        String cat = detection.display();
        String learnedNote = detection.isFromLearned() ? "\n🧠 _Узнал по памяти_" : "";
        String confNote = (!detection.isFromLearned() && detection.getConfidence() < 1.0)
                ? " _(~" + Math.round(detection.getConfidence() * 100) + "%)_" : "";

        String[] reactions = {"✅", "💾", "📌"};
        String reaction = reactions[RND.nextInt(reactions.length)];

        // Pace hint — one line showing cycle status
        String paceHint = paydayService.getPaceHint(user).map(h -> "\n" + h).orElse("");

        bot.sendMessage(chatId,
                reaction + " *Записал!*\n\n" +
                        "📝 " + p.description + "\n" +
                        "💸 −" + fmt(p.amount) + "\n" +
                        "🏷 " + cat + confNote + learnedNote + "\n" +
                        "💳 Баланс: *" + fmt(user.getBalance()) + "*" + paceHint,
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
                        "📝 " + p.description + "\n" +
                        "💰 +" + fmt(p.amount) + "\n" +
                        "💳 Баланс: *" + fmt(user.getBalance()) + "*" + cycleHint,
                KeyboardFactory.mainMenu());
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