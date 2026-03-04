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
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHandler {

    private static final String CANCEL_BUTTON = "❌ Отменить действие";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final UserService            userService;
    private final UserStateService       stateService;
    private final TransactionService     transactionService;
    private final SubscriptionService    subscriptionService;
    private final ReportService          reportService;
    private final FinancialTipsService   tipsService;

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

        // Кнопка отмены — обрабатывается ПЕРВОЙ, до FSM
        if (CANCEL_BUTTON.equals(text)) {
            stateService.reset(telegramId);
            bot.sendMessage(chatId, "Действие отменено.", KeyboardFactory.mainMenu());
            return;
        }

        // FSM — ожидание ввода
        UserState state = stateService.getState(telegramId);
        switch (state) {
            case WAITING_EXPENSE        -> { handleExpenseInput(user, text, chatId, telegramId, bot);    return; }
            case WAITING_INCOME         -> { handleIncomeInput(user, text, chatId, telegramId, bot);     return; }
            case WAITING_SUB_NAME       -> { handleSubName(text, chatId, telegramId, bot);               return; }
            case WAITING_SUB_AMOUNT     -> { handleSubAmount(text, chatId, telegramId, bot);             return; }
            case WAITING_SUB_START_DATE -> { handleSubStartDate(text, chatId, telegramId, bot);          return; }
            case WAITING_SUB_END_DATE   -> { handleSubEndDate(user, text, chatId, telegramId, bot);      return; }
            default -> {}
        }

        // Главное меню
        switch (text) {
            case "💸 Расход" -> startExpense(chatId, telegramId, bot);
            case "💰 Доход"  -> startIncome(chatId, telegramId, bot);
            default -> bot.sendMessage(chatId,
                    "Нажми кнопку или используй команды:\n" +
                            "/balance · /stats · /subscriptions · /help",
                    KeyboardFactory.mainMenu());
        }
    }

    // ================================================================
    // Expense flow
    // ================================================================

    private void startExpense(long chatId, long telegramId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_EXPENSE);
        bot.sendMessage(chatId,
                "Введите расход в формате:\n*название сумма*\n\nПример:\n`шаурма 300`",
                KeyboardFactory.cancelMenu());
    }

    private void handleExpenseInput(User user, String text, long chatId,
                                    long telegramId, MonetkaBot bot) {
        ParseResult parsed = parse(text);
        if (parsed == null) {
            bot.sendMessage(chatId,
                    "Введите расход в формате:\n*название сумма*\n\nПример:\n`шаурма 300`",
                    KeyboardFactory.cancelMenu());
            return;
        }

        Transaction tx = transactionService.addExpense(user, parsed.amount, parsed.description);
        stateService.reset(telegramId);

        String cat = tx.getCategory() != null ? tx.getCategory().getDisplayName() : "💰 Прочее";
        bot.sendMessage(chatId,
                "✅ *Расход сохранён*\n\n" +
                        "📝 " + parsed.description + "\n" +
                        "💸 −" + fmt(parsed.amount) + "\n" +
                        "🏷 " + cat + "\n" +
                        "💳 Баланс: " + fmt(user.getBalance()),
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // Income flow
    // ================================================================

    private void startIncome(long chatId, long telegramId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_INCOME);
        bot.sendMessage(chatId,
                "Введите доход в формате:\n*название сумма*\n\nПример:\n`зарплата 150000`",
                KeyboardFactory.cancelMenu());
    }

    private void handleIncomeInput(User user, String text, long chatId,
                                   long telegramId, MonetkaBot bot) {
        ParseResult parsed = parse(text);
        if (parsed == null) {
            bot.sendMessage(chatId,
                    "Введите доход в формате:\n*название сумма*\n\nПример:\n`зарплата 150000`",
                    KeyboardFactory.cancelMenu());
            return;
        }

        transactionService.addIncome(user, parsed.amount, parsed.description);
        stateService.reset(telegramId);

        bot.sendMessage(chatId,
                "✅ *Доход сохранён*\n\n" +
                        "📝 " + parsed.description + "\n" +
                        "💰 +" + fmt(parsed.amount) + "\n" +
                        "💳 Баланс: " + fmt(user.getBalance()),
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // Subscription wizard
    // ================================================================

    private void handleSubName(String text, long chatId, long telegramId, MonetkaBot bot) {
        stateService.putData(telegramId, "sub_name", text.trim());
        stateService.setState(telegramId, UserState.WAITING_SUB_AMOUNT);
        bot.sendMessage(chatId,
                "💰 Сколько стоит в месяц?\n\nПример: `1500`",
                KeyboardFactory.cancelMenu());
    }

    private void handleSubAmount(String text, long chatId, long telegramId, MonetkaBot bot) {
        try {
            BigDecimal amount = new BigDecimal(text.trim().replace(",", "."));
            stateService.putData(telegramId, "sub_amount", amount.toPlainString());
            stateService.setState(telegramId, UserState.WAITING_SUB_START_DATE);
            bot.sendMessage(chatId,
                    "📅 Дата начала подписки?\n\nФормат: `дд.мм.гггг`\nПример: `01.03.2026`",
                    KeyboardFactory.cancelMenu());
        } catch (NumberFormatException e) {
            bot.sendMessage(chatId, "Введите число, например: `1500`", KeyboardFactory.cancelMenu());
        }
    }

    private void handleSubStartDate(String text, long chatId, long telegramId, MonetkaBot bot) {
        try {
            LocalDate startDate = LocalDate.parse(text.trim(), DATE_FMT);
            stateService.putData(telegramId, "sub_start", startDate.toString());
            stateService.setState(telegramId, UserState.WAITING_SUB_END_DATE);
            bot.sendMessage(chatId,
                    "📅 Дата окончания подписки?\n\n" +
                            "Формат: `дд.мм.гггг`\nПример: `01.03.2027`\n\n" +
                            "Или напиши `бессрочно` если нет даты окончания.",
                    KeyboardFactory.cancelMenu());
        } catch (DateTimeParseException e) {
            bot.sendMessage(chatId,
                    "Неверный формат. Введите дату в виде:\n`дд.мм.гггг`\nПример: `01.03.2026`",
                    KeyboardFactory.cancelMenu());
        }
    }

    private void handleSubEndDate(User user, String text, long chatId,
                                  long telegramId, MonetkaBot bot) {
        LocalDate endDate = null;

        if (!text.trim().equalsIgnoreCase("бессрочно")) {
            try {
                endDate = LocalDate.parse(text.trim(), DATE_FMT);
            } catch (DateTimeParseException e) {
                bot.sendMessage(chatId,
                        "Неверный формат. Введите дату `дд.мм.гггг` или напишите `бессрочно`",
                        KeyboardFactory.cancelMenu());
                return;
            }
        }

        String     name      = stateService.getData(telegramId, "sub_name");
        BigDecimal amount    = new BigDecimal(stateService.getData(telegramId, "sub_amount"));
        LocalDate  startDate = LocalDate.parse(stateService.getData(telegramId, "sub_start"));

        Subscription sub = subscriptionService.create(user, name, amount, startDate, endDate);
        stateService.reset(telegramId);

        String endInfo = endDate != null
                ? "до " + endDate.format(DATE_FMT)
                : "бессрочно ♾";

        String tip = tipsService.tipForSubscription(name, amount);

        bot.sendMessage(chatId,
                "✅ *Подписка добавлена!*\n\n" +
                        "📝 " + sub.getName() + "\n" +
                        "💸 " + fmt(amount) + "/мес\n" +
                        "📅 С " + startDate.format(DATE_FMT) + " — " + endInfo + "\n\n" +
                        tip,
                KeyboardFactory.mainMenu());
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

    private String fmt(BigDecimal amount) {
        return String.format("%,.0f сом", amount);
    }

    private static final class ParseResult {
        final String description;
        final BigDecimal amount;
        ParseResult(String description, BigDecimal amount) {
            this.description = description;
            this.amount = amount;
        }
    }
}