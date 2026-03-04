package com.monetka.bot.handler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.Subscription;
import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.enums.UserState;
import com.monetka.model.enums.UserStatus;
import com.monetka.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHandler {

    private static final String CANCEL_BUTTON = "❌ Отменить действие";
    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final UserService              userService;
    private final UserStateService         stateService;
    private final TransactionService       transactionService;
    private final SubscriptionService      subscriptionService;
    private final ReportService            reportService;
    private final FinancialTipsService     tipsService;
    private final CategoryDetectionService detectionService;

    public void handle(Message message, MonetkaBot bot) {
        long   chatId     = message.getChatId();
        long   telegramId = message.getFrom().getId();
        String text       = message.getText();

        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            bot.sendText(chatId, "Используй /start для регистрации.");
            return;
        }

        User user = userOpt.get();

        if (user.getStatus() == UserStatus.BLOCKED) {
            bot.sendText(chatId, "Ваш доступ к Monetka был заблокирован администратором.");
            return;
        }
        if (user.getStatus() == UserStatus.PENDING) {
            bot.sendText(chatId, "⏳ Твоя заявка ожидает одобрения администратора.");
            return;
        }

        // Отмена — всегда первой
        if (CANCEL_BUTTON.equals(text)) {
            stateService.reset(telegramId);
            bot.sendMessage(chatId, "Действие отменено.", KeyboardFactory.mainMenu());
            return;
        }

        // FSM
        UserState state = stateService.getState(telegramId);
        switch (state) {
            case WAITING_EXPENSE        -> { handleExpenseInput(user, text, chatId, telegramId, bot);  return; }
            case WAITING_INCOME         -> { handleIncomeInput(user, text, chatId, telegramId, bot);   return; }
            case WAITING_SUB_NAME       -> { handleSubName(text, chatId, telegramId, bot);             return; }
            case WAITING_SUB_AMOUNT     -> { handleSubAmount(text, chatId, telegramId, bot);           return; }
            case WAITING_SUB_START_DATE -> { handleSubStartDate(text, chatId, telegramId, bot);        return; }
            case WAITING_SUB_END_DATE   -> { handleSubEndDate(user, text, chatId, telegramId, bot);    return; }
            default -> {}
        }

        switch (text) {
            case "💸 Расход" -> startExpense(chatId, telegramId, bot);
            case "💰 Доход"  -> startIncome(chatId, telegramId, bot);
            default -> bot.sendMessage(chatId,
                    "Используй кнопки ниже или команды:\n/balance /stats /subscriptions /help",
                    KeyboardFactory.mainMenu());
        }
    }

    // ================================================================
    // Expense
    // ================================================================

    private void startExpense(long chatId, long telegramId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_EXPENSE);
        bot.sendMessage(chatId,
                "Введи расход в формате:\n*название сумма*\n\nПример: `шаурма 300`",
                KeyboardFactory.cancelMenu());
    }

    private void handleExpenseInput(User user, String text, long chatId,
                                    long telegramId, MonetkaBot bot) {
        ParseResult p = parse(text);
        if (p == null) {
            bot.sendMessage(chatId,
                    "Введи расход в формате:\n*название сумма*\n\nПример: `шаурма 300`",
                    KeyboardFactory.cancelMenu());
            return;
        }

        // Детектируем категорию
        CategoryDetectionService.DetectionResult detection =
                detectionService.detectCategory(p.description, user.getTelegramId());

        // Сохраняем данные в сессию
        stateService.putData(telegramId, "expense_desc",   p.description);
        stateService.putData(telegramId, "expense_amount", p.amount.toPlainString());

        if (detection.isConfident() && detection.getCategory() != null
                && !detection.getCategory().isDefault()) {
            // Уверены — сохраняем сразу
            saveExpense(user, p, detection, chatId, telegramId, bot);
        } else {
            // Не уверены — спрашиваем
            stateService.setState(telegramId, UserState.WAITING_CATEGORY_CHOICE);
            bot.sendMessage(chatId,
                    "🤔 Не смог определить категорию для *" + p.description + "*\n\n" +
                            "Выбери из списка:",
                    KeyboardFactory.categoryChoice(detectionService.getAllCategories()));
        }
    }

    private void saveExpense(User user, ParseResult p,
                             CategoryDetectionService.DetectionResult detection,
                             long chatId, long telegramId, MonetkaBot bot) {
        Transaction tx = transactionService.addExpense(user, p.amount, p.description);
        stateService.reset(telegramId);

        String cat = detection.display();
        String conf = detection.getConfidence() < 1.0
                ? " _(~" + Math.round(detection.getConfidence() * 100) + "%)_"
                : "";

        bot.sendMessage(chatId,
                "✅ *Расход сохранён!*\n\n" +
                        "📝 " + p.description + "\n" +
                        "💸 −" + fmt(p.amount) + "\n" +
                        "🏷 " + cat + conf + "\n" +
                        "💳 Баланс: " + fmt(user.getBalance()),
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // Income
    // ================================================================

    private void startIncome(long chatId, long telegramId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_INCOME);
        bot.sendMessage(chatId,
                "Введи доход в формате:\n*название сумма*\n\nПример: `зарплата 150000`",
                KeyboardFactory.cancelMenu());
    }

    private void handleIncomeInput(User user, String text, long chatId,
                                   long telegramId, MonetkaBot bot) {
        ParseResult p = parse(text);
        if (p == null) {
            bot.sendMessage(chatId,
                    "Введи доход в формате:\n*название сумма*\n\nПример: `зарплата 150000`",
                    KeyboardFactory.cancelMenu());
            return;
        }
        transactionService.addIncome(user, p.amount, p.description);
        stateService.reset(telegramId);

        bot.sendMessage(chatId,
                "✅ *Доход сохранён!*\n\n" +
                        "📝 " + p.description + "\n" +
                        "💰 +" + fmt(p.amount) + "\n" +
                        "💳 Баланс: " + fmt(user.getBalance()),
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // Subscription wizard
    // ================================================================

    private void handleSubName(String text, long chatId, long telegramId, MonetkaBot bot) {
        stateService.putData(telegramId, "sub_name", text.trim());
        stateService.setState(telegramId, UserState.WAITING_SUB_AMOUNT);
        bot.sendMessage(chatId, "Сумма в месяц (например: `1500`):", KeyboardFactory.cancelMenu());
    }

    private void handleSubAmount(String text, long chatId, long telegramId, MonetkaBot bot) {
        try {
            BigDecimal amount = new BigDecimal(text.trim().replace(",", "."));
            stateService.putData(telegramId, "sub_amount", amount.toPlainString());
            stateService.setState(telegramId, UserState.WAITING_SUB_START_DATE);
            bot.sendMessage(chatId,
                    "Дата начала подписки в формате *ДД.ММ.ГГГГ*\n\nИли нажми _Сегодня_:",
                    KeyboardFactory.cancelWithSkip("Сегодня"));
        } catch (NumberFormatException e) {
            bot.sendMessage(chatId, "Введи число, например: `1500`", KeyboardFactory.cancelMenu());
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
                        "Неверный формат. Введи *ДД.ММ.ГГГГ*\nПример: `" + LocalDate.now().format(D_FMT) + "`",
                        KeyboardFactory.cancelWithSkip("Сегодня"));
                return;
            }
        }
        stateService.putData(telegramId, "sub_start", startDate.toString());
        stateService.setState(telegramId, UserState.WAITING_SUB_END_DATE);
        bot.sendMessage(chatId,
                "Дата окончания подписки *ДД.ММ.ГГГГ*\n\nИли нажми _Бессрочно_:",
                KeyboardFactory.cancelWithSkip("Бессрочно"));
    }

    private void handleSubEndDate(User user, String text, long chatId,
                                  long telegramId, MonetkaBot bot) {
        LocalDate endDate = null;
        if (!text.equalsIgnoreCase("бессрочно") && !text.startsWith("⏭")) {
            endDate = parseDate(text);
            if (endDate == null) {
                bot.sendMessage(chatId,
                        "Неверный формат. Введи *ДД.ММ.ГГГГ*\nИли нажми _Бессрочно_:",
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

        StringBuilder reply = new StringBuilder("✅ *Подписка добавлена!*\n\n");
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
            String desc = String.join(" ", Arrays.copyOf(tokens, tokens.length - 1)).trim();
            return new ParseResult(desc.isBlank() ? "Без описания" : desc, amount);
        } catch (NumberFormatException ignored) {}

        try {
            BigDecimal amount = new BigDecimal(tokens[0].replace(",", "."));
            String desc = String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)).trim();
            return new ParseResult(desc.isBlank() ? "Без описания" : desc, amount);
        } catch (NumberFormatException ignored) {}

        return null;
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim(), D_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String fmt(BigDecimal amount) {
        return String.format("%,.0f сом", amount);
    }

    private static final class ParseResult {
        final String description;
        final BigDecimal amount;
        ParseResult(String d, BigDecimal a) { description = d; amount = a; }
    }
}