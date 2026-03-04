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
import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHandler {

    private static final String CANCEL_BUTTON = "❌ Отменить действие";

    private final UserService         userService;
    private final UserStateService    stateService;
    private final TransactionService  transactionService;
    private final SubscriptionService subscriptionService;
    private final ReportService       reportService;

    public void handle(Message message, MonetkaBot bot) {
        long   chatId     = message.getChatId();
        long   telegramId = message.getFrom().getId();
        String text       = message.getText();

        // ---- Пользователь не зарегистрирован ----
        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            bot.sendText(chatId, "Используй /start для регистрации.");
            return;
        }

        User user = userOpt.get();

        // ---- Проверка статуса ----
        if (user.getStatus() == UserStatus.BLOCKED) {
            bot.sendText(chatId,
                    "Ваш доступ к Monetka был заблокирован администратором.");
            return;
        }

        if (user.getStatus() == UserStatus.PENDING) {
            bot.sendText(chatId,
                    "⏳ Ваша заявка ожидает одобрения администратора.");
            return;
        }

        // ---- Кнопка отмены — обрабатывается ПЕРВОЙ, до FSM ----
        if (CANCEL_BUTTON.equals(text)) {
            stateService.reset(telegramId);
            bot.sendMessage(chatId, "Действие отменено.", KeyboardFactory.mainMenu());
            return;
        }

        // ---- FSM: ожидание ввода ----
        UserState state = stateService.getState(telegramId);
        switch (state) {
            case WAITING_EXPENSE    -> { handleExpenseInput(user, text, chatId, telegramId, bot); return; }
            case WAITING_INCOME     -> { handleIncomeInput(user, text, chatId, telegramId, bot);  return; }
            case WAITING_SUB_NAME   -> { handleSubName(user, text, chatId, telegramId, bot);      return; }
            case WAITING_SUB_AMOUNT -> { handleSubAmount(user, text, chatId, telegramId, bot);    return; }
            case WAITING_SUB_DAY    -> { handleSubDay(user, text, chatId, telegramId, bot);       return; }
            default -> {}
        }

        // ---- Главное меню — только 2 кнопки ----
        switch (text) {
            case "💸 Расход" -> startExpense(chatId, telegramId, bot);
            case "💰 Доход"  -> startIncome(chatId, telegramId, bot);
            default -> bot.sendMessage(chatId,
                    "Нажми кнопку ниже или используй команды:\n" +
                            "/balance — баланс\n" +
                            "/stats — статистика\n" +
                            "/subscriptions — подписки\n" +
                            "/help — справка",
                    KeyboardFactory.mainMenu());
        }
    }

    // ================================================================
    // Expense flow
    // ================================================================

    private void startExpense(long chatId, long telegramId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_EXPENSE);
        bot.sendMessage(chatId,
                "Введите расход в формате:\n" +
                        "*название сумма*\n\n" +
                        "Пример:\n`шаурма 300`",
                KeyboardFactory.cancelMenu());
    }

    private void handleExpenseInput(User user, String text, long chatId,
                                    long telegramId, MonetkaBot bot) {
        ParseResult parsed = parseAmountText(text);
        if (parsed == null) {
            bot.sendMessage(chatId,
                    "Введите расход в формате:\n" +
                            "*название сумма*\n\n" +
                            "Пример:\n`шаурма 300`",
                    KeyboardFactory.cancelMenu());
            return;
        }

        Transaction tx = transactionService.addExpense(user, parsed.amount, parsed.description);
        stateService.reset(telegramId);

        String categoryName = tx.getCategory() != null
                ? tx.getCategory().getDisplayName() : "💰 Прочее";

        bot.sendMessage(chatId,
                "✅ Расход сохранён!\n\n" +
                        "📝 " + parsed.description + "\n" +
                        "💸 -" + fmt(parsed.amount) + "\n" +
                        "🏷 " + categoryName + "\n" +
                        "💳 Баланс: " + fmt(user.getBalance()),
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // Income flow
    // ================================================================

    private void startIncome(long chatId, long telegramId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_INCOME);
        bot.sendMessage(chatId,
                "Введите доход в формате:\n" +
                        "*название сумма*\n\n" +
                        "Пример:\n`зарплата 150000`",
                KeyboardFactory.cancelMenu());
    }

    private void handleIncomeInput(User user, String text, long chatId,
                                   long telegramId, MonetkaBot bot) {
        ParseResult parsed = parseAmountText(text);
        if (parsed == null) {
            bot.sendMessage(chatId,
                    "Введите доход в формате:\n" +
                            "*название сумма*\n\n" +
                            "Пример:\n`зарплата 150000`",
                    KeyboardFactory.cancelMenu());
            return;
        }

        transactionService.addIncome(user, parsed.amount, parsed.description);
        stateService.reset(telegramId);

        bot.sendMessage(chatId,
                "✅ Доход сохранён!\n\n" +
                        "📝 " + parsed.description + "\n" +
                        "💰 +" + fmt(parsed.amount) + "\n" +
                        "💳 Баланс: " + fmt(user.getBalance()),
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // Subscription wizard
    // ================================================================

    private void handleSubName(User user, String text, long chatId,
                               long telegramId, MonetkaBot bot) {
        stateService.putData(telegramId, "sub_name", text.trim());
        stateService.setState(telegramId, UserState.WAITING_SUB_AMOUNT);
        bot.sendMessage(chatId,
                "Сумма в месяц (например: `3000`):",
                KeyboardFactory.cancelMenu());
    }

    private void handleSubAmount(User user, String text, long chatId,
                                 long telegramId, MonetkaBot bot) {
        try {
            BigDecimal amount = new BigDecimal(text.trim().replace(",", "."));
            stateService.putData(telegramId, "sub_amount", amount.toPlainString());
            stateService.setState(telegramId, UserState.WAITING_SUB_DAY);
            bot.sendMessage(chatId,
                    "Какого числа списывается? (1–28):",
                    KeyboardFactory.cancelMenu());
        } catch (NumberFormatException e) {
            bot.sendMessage(chatId,
                    "Введите число, например: `3000`",
                    KeyboardFactory.cancelMenu());
        }
    }

    private void handleSubDay(User user, String text, long chatId,
                              long telegramId, MonetkaBot bot) {
        try {
            int day = Integer.parseInt(text.trim());
            if (day < 1 || day > 28) {
                bot.sendMessage(chatId,
                        "Число должно быть от 1 до 28:",
                        KeyboardFactory.cancelMenu());
                return;
            }

            String name       = stateService.getData(telegramId, "sub_name");
            BigDecimal amount = new BigDecimal(stateService.getData(telegramId, "sub_amount"));

            Subscription sub = subscriptionService.create(user, name, amount, day);
            stateService.reset(telegramId);

            bot.sendMessage(chatId,
                    "✅ Подписка добавлена!\n\n" +
                            "📝 " + sub.getName() + "\n" +
                            "💸 " + fmt(sub.getAmount()) + "/мес\n" +
                            "📅 Каждое " + day + " число",
                    KeyboardFactory.mainMenu());

        } catch (NumberFormatException e) {
            bot.sendMessage(chatId,
                    "Введите число от 1 до 28:",
                    KeyboardFactory.cancelMenu());
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    /**
     * Parse "шаурма 300" или "300 шаурма".
     * Возвращает null если формат неверный (только слово без числа).
     */
    private ParseResult parseAmountText(String text) {
        if (text == null || text.isBlank()) return null;
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length < 2) return null; // одно слово без суммы — ошибка

        // Последний токен — число
        try {
            BigDecimal amount = new BigDecimal(tokens[tokens.length - 1].replace(",", "."));
            String description = String.join(" ",
                    Arrays.copyOf(tokens, tokens.length - 1)).trim();
            if (description.isBlank()) description = "Без описания";
            return new ParseResult(description, amount);
        } catch (NumberFormatException ignored) {}

        // Первый токен — число
        try {
            BigDecimal amount = new BigDecimal(tokens[0].replace(",", "."));
            String description = String.join(" ",
                    Arrays.copyOfRange(tokens, 1, tokens.length)).trim();
            if (description.isBlank()) description = "Без описания";
            return new ParseResult(description, amount);
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