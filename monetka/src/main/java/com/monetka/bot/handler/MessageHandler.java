package com.monetka.bot.handler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.Subscription;
import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.enums.UserState;
import com.monetka.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHandler {

    private final UserService         userService;
    private final UserStateService    stateService;
    private final TransactionService  transactionService;
    private final SubscriptionService subscriptionService;
    private final ReportService       reportService;

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

        switch (user.getStatus()) {
            case PENDING -> { bot.sendText(chatId, "⏳ Ожидай подтверждения от администратора."); return; }
            case BLOCKED -> { bot.sendText(chatId, "⛔ Доступ заблокирован."); return; }
            default      -> {}
        }

        UserState state = stateService.getState(telegramId);

        // ---- FSM: handle awaiting input states first ----
        switch (state) {
            case WAITING_EXPENSE    -> { handleExpenseInput(user, text, chatId, telegramId, bot); return; }
            case WAITING_INCOME     -> { handleIncomeInput(user, text, chatId, telegramId, bot);  return; }
            case WAITING_SUB_NAME   -> { handleSubName(user, text, chatId, telegramId, bot);      return; }
            case WAITING_SUB_AMOUNT -> { handleSubAmount(user, text, chatId, telegramId, bot);    return; }
            case WAITING_SUB_DAY    -> { handleSubDay(user, text, chatId, telegramId, bot);       return; }
            default                 -> {}
        }

        // ---- Main menu buttons ----
        switch (text) {
            case "💸 Расход"     -> startExpense(chatId, telegramId, bot);
            case "💰 Доход"      -> startIncome(chatId, telegramId, bot);
            case "📊 Статистика" -> showStats(user, chatId, bot);
            case "💳 Баланс"     -> showBalance(user, chatId, bot);
            case "🔄 Подписки"   -> showSubscriptions(user, chatId, bot);
            case "❌ Отмена"     -> { stateService.reset(telegramId);
                                      bot.sendMessage(chatId, "Отменено.", KeyboardFactory.mainMenu()); }
            case "❓ Помощь"     -> bot.sendMarkdown(chatId, helpText());
            default -> bot.sendMessage(chatId,
                "Используй меню ниже 👇",
                KeyboardFactory.mainMenu());
        }
    }

    // ---- Expense flow ----

    private void startExpense(long chatId, long telegramId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_EXPENSE);
        bot.sendMessage(chatId,
            "Введи расход в формате:\n`шаурма 300`",
            KeyboardFactory.cancelOnly());
    }

    private void handleExpenseInput(User user, String text, long chatId,
                                    long telegramId, MonetkaBot bot) {
        ParseResult parsed = parseAmountText(text);
        if (parsed == null) {
            bot.sendText(chatId, "Не понял формат. Попробуй: `шаурма 300`");
            return;
        }

        Transaction tx = transactionService.addExpense(user, parsed.amount(), parsed.description());
        stateService.reset(telegramId);

        String categoryName = tx.getCategory() != null
            ? tx.getCategory().getDisplayName() : "💰 Прочее";

        bot.sendMessage(chatId,
            "✅ Расход сохранён!\n\n" +
            "📝 " + parsed.description() + "\n" +
            "💸 -" + fmt(parsed.amount()) + "\n" +
            "🏷 " + categoryName + "\n" +
            "💳 Баланс: " + fmt(user.getBalance()),
            KeyboardFactory.mainMenu());
    }

    // ---- Income flow ----

    private void startIncome(long chatId, long telegramId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_INCOME);
        bot.sendMessage(chatId,
            "Введи доход в формате:\n`зарплата 150000`",
            KeyboardFactory.cancelOnly());
    }

    private void handleIncomeInput(User user, String text, long chatId,
                                   long telegramId, MonetkaBot bot) {
        ParseResult parsed = parseAmountText(text);
        if (parsed == null) {
            bot.sendText(chatId, "Не понял формат. Попробуй: `зарплата 150000`");
            return;
        }

        transactionService.addIncome(user, parsed.amount(), parsed.description());
        stateService.reset(telegramId);

        bot.sendMessage(chatId,
            "✅ Доход сохранён!\n\n" +
            "📝 " + parsed.description() + "\n" +
            "💰 +" + fmt(parsed.amount()) + "\n" +
            "💳 Баланс: " + fmt(user.getBalance()),
            KeyboardFactory.mainMenu());
    }

    // ---- Subscription wizard ----

    private void startSubscription(long chatId, long telegramId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_SUB_NAME);
        bot.sendMessage(chatId, "Введи название подписки\n(например: Netflix):",
            KeyboardFactory.cancelOnly());
    }

    private void handleSubName(User user, String text, long chatId,
                               long telegramId, MonetkaBot bot) {
        stateService.putData(telegramId, "sub_name", text.trim());
        stateService.setState(telegramId, UserState.WAITING_SUB_AMOUNT);
        bot.sendText(chatId, "Сумма в месяц (например: 3000):");
    }

    private void handleSubAmount(User user, String text, long chatId,
                                 long telegramId, MonetkaBot bot) {
        try {
            BigDecimal amount = new BigDecimal(text.trim().replace(",", "."));
            stateService.putData(telegramId, "sub_amount", amount.toPlainString());
            stateService.setState(telegramId, UserState.WAITING_SUB_DAY);
            bot.sendText(chatId, "Какого числа списывается? (1–28):");
        } catch (NumberFormatException e) {
            bot.sendText(chatId, "Введи число, например: 3000");
        }
    }

    private void handleSubDay(User user, String text, long chatId,
                              long telegramId, MonetkaBot bot) {
        try {
            int day = Integer.parseInt(text.trim());
            if (day < 1 || day > 28) {
                bot.sendText(chatId, "Число должно быть от 1 до 28");
                return;
            }

            String name   = stateService.getData(telegramId, "sub_name");
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
            bot.sendText(chatId, "Введи число от 1 до 28");
        }
    }

    // ---- Stats and balance ----

    private void showStats(User user, long chatId, MonetkaBot bot) {
        bot.sendMessage(chatId,
            reportService.buildMonthStats(user),
            KeyboardFactory.statsPeriod());
    }

    private void showBalance(User user, long chatId, MonetkaBot bot) {
        bot.sendMarkdown(chatId,
            "💳 *Твой баланс:*\n\n*" + fmt(user.getBalance()) + "*");
    }

    private void showSubscriptions(User user, long chatId, MonetkaBot bot) {
        List<Subscription> subs = subscriptionService.getActiveSubscriptions(user);
        bot.sendMessage(chatId,
            reportService.buildSubscriptionsList(user),
            KeyboardFactory.subscriptionActions(subs));
    }

    // ---- Helpers ----

    /** Parse "шаурма 300" or "300 кофе" → ParseResult */
    private ParseResult parseAmountText(String text) {
        if (text == null || text.isBlank()) return null;
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length == 0) return null;

        // Try last token as amount
        try {
            BigDecimal amount = new BigDecimal(tokens[tokens.length - 1].replace(",", "."));
            String description = String.join(" ",
                java.util.Arrays.copyOf(tokens, tokens.length - 1)).trim();
            if (description.isBlank()) description = "Без описания";
            return new ParseResult(description, amount);
        } catch (NumberFormatException ignored) {}

        // Try first token as amount
        try {
            BigDecimal amount = new BigDecimal(tokens[0].replace(",", "."));
            String description = String.join(" ",
                java.util.Arrays.copyOfRange(tokens, 1, tokens.length)).trim();
            if (description.isBlank()) description = "Без описания";
            return new ParseResult(description, amount);
        } catch (NumberFormatException ignored) {}

        return null;
    }

    private String fmt(BigDecimal amount) {
        return String.format("%,.0f ₸", amount);
    }

    private String helpText() {
        return """
            *Monetka — финансовый помощник* 💰
            
            Нажми кнопку и введи:
            `шаурма 300` или `300 шаурма`
            
            📊 *Статистика* — расходы за месяц
            💳 *Баланс* — текущий баланс
            🔄 *Подписки* — ежемесячные расходы
            """;
    }

    private record ParseResult(String description, BigDecimal amount) {}
}
