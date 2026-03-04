package com.monetka.bot.handler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.config.BotProperties;
import com.monetka.model.Subscription;
import com.monetka.model.User;
import com.monetka.model.enums.UserState;
import com.monetka.model.enums.UserStatus;
import com.monetka.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandHandler {

    private final UserService         userService;
    private final UserStateService    stateService;
    private final ReportService       reportService;
    private final SubscriptionService subscriptionService;
    private final BotProperties       botProperties;

    public void handle(Message message, MonetkaBot bot) {
        String text      = message.getText();
        String command   = extractCommand(text);
        long   chatId    = message.getChatId();
        long   telegramId = message.getFrom().getId();

        switch (command) {
            case "/start"         -> handleStart(message, chatId, telegramId, bot);
            case "/help"          -> handleHelp(chatId, telegramId, bot);
            case "/cancel"        -> handleCancel(chatId, telegramId, bot);
            case "/balance"       -> handleBalance(chatId, telegramId, bot);
            case "/stats"         -> handleStats(chatId, telegramId, bot);
            case "/subscriptions" -> handleSubscriptions(chatId, telegramId, bot);
            // Admin
            case "/pending"       -> handlePending(chatId, telegramId, bot);
            case "/blocked"       -> handleBlockedList(chatId, telegramId, bot);
            case "/approve"       -> handleApprove(text, chatId, telegramId, bot);
            case "/block"         -> handleBlock(text, chatId, telegramId, bot);
            case "/unblock"       -> handleUnblock(text, chatId, telegramId, bot);
            default               -> bot.sendText(chatId,
                    "Неизвестная команда. Напиши /help");
        }
    }

    // ================================================================
    // /start
    // ================================================================

    private void handleStart(Message message, long chatId, long telegramId, MonetkaBot bot) {
        org.telegram.telegrambots.meta.api.objects.User from = message.getFrom();

        User user = userService.registerOrGet(
                telegramId,
                from.getUserName(),
                from.getFirstName(),
                from.getLastName()
        );

        stateService.reset(telegramId);

        switch (user.getStatus()) {
            case PENDING -> {
                bot.sendText(chatId,
                        "👋 Привет, " + user.getDisplayName() + "!\n\n" +
                                "Твоя заявка отправлена администратору.\n" +
                                "Ожидай подтверждения ⏳");
                notifyAdmins(user, bot);
            }
            case APPROVED -> bot.sendMessage(chatId,
                    "👋 Привет, " + user.getDisplayName() + "! Я готов к работе 💪",
                    KeyboardFactory.mainMenu());
            case BLOCKED -> bot.sendText(chatId,
                    "Ваш доступ к Monetka был заблокирован администратором.");
        }
    }

    // ================================================================
    // /help
    // ================================================================

    private void handleHelp(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;

        bot.sendMarkdown(chatId,
                "*Monetka — финансовый помощник* 💰\n\n" +
                        "*Как добавить расход или доход:*\n" +
                        "Нажми кнопку и введи в формате:\n" +
                        "`название сумма`\n\n" +
                        "Пример:\n" +
                        "`шаурма 300`\n" +
                        "`зарплата 150000`\n\n" +
                        "*Команды:*\n" +
                        "/balance — текущий баланс\n" +
                        "/stats — статистика за месяц\n" +
                        "/subscriptions — ежемесячные расходы\n" +
                        "/cancel — отменить текущее действие\n" +
                        "/help — эта справка"
        );
    }

    // ================================================================
    // /cancel
    // ================================================================

    private void handleCancel(long chatId, long telegramId, MonetkaBot bot) {
        stateService.reset(telegramId);
        bot.sendMessage(chatId, "Действие отменено.", KeyboardFactory.mainMenu());
    }

    // ================================================================
    // /balance
    // ================================================================

    private void handleBalance(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;

        userService.findByTelegramId(telegramId).ifPresent(user ->
                bot.sendMarkdown(chatId,
                        "💳 *Твой баланс:*\n\n*" + fmt(user.getBalance()) + "*")
        );
    }

    // ================================================================
    // /stats
    // ================================================================

    private void handleStats(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;

        userService.findByTelegramId(telegramId).ifPresent(user ->
                bot.sendMessage(chatId,
                        reportService.buildMonthStats(user),
                        KeyboardFactory.statsPeriod())
        );
    }

    // ================================================================
    // /subscriptions
    // ================================================================

    private void handleSubscriptions(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;

        userService.findByTelegramId(telegramId).ifPresent(user -> {
            List<Subscription> subs = subscriptionService.getActiveSubscriptions(user);
            bot.sendMessage(chatId,
                    reportService.buildSubscriptionsList(user),
                    KeyboardFactory.subscriptionActions(subs));
        });
    }

    // ================================================================
    // Admin: /pending
    // ================================================================

    private void handlePending(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;

        List<User> pending = userService.getPendingUsers();

        if (pending.isEmpty()) {
            bot.sendText(chatId, "⏳ Нет пользователей, ожидающих одобрения.");
            return;
        }

        StringBuilder sb = new StringBuilder("⏳ *Ожидают одобрения:* " + pending.size() + "\n\n");
        for (User u : pending) {
            sb.append("• ").append(u.getDisplayName())
                    .append(" — `").append(u.getTelegramId()).append("`\n");
        }
        sb.append("\nОдобрить: `/approve telegramId`");

        bot.sendMarkdown(chatId, sb.toString());
    }

    // ================================================================
    // Admin: /blocked
    // ================================================================

    private void handleBlockedList(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;

        List<User> blocked = userService.getBlockedUsers();

        if (blocked.isEmpty()) {
            bot.sendText(chatId, "✅ Заблокированных пользователей нет.");
            return;
        }

        StringBuilder sb = new StringBuilder("🚫 *Заблокированы:* " + blocked.size() + "\n\n");
        for (User u : blocked) {
            sb.append("• ").append(u.getDisplayName())
                    .append(" — `").append(u.getTelegramId()).append("`\n");
        }
        sb.append("\nРазблокировать: `/unblock telegramId`");

        bot.sendMarkdown(chatId, sb.toString());
    }

    // ================================================================
    // Admin: /approve {telegramId}
    // ================================================================

    private void handleApprove(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;

        Long targetId = parseId(text, chatId, bot);
        if (targetId == null) return;

        if (userService.approveUser(targetId)) {
            bot.sendText(chatId, "✅ Пользователь " + targetId + " одобрен.");
            bot.sendMessage(targetId,
                    "✅ Ваш доступ к Monetka подтверждён! Добро пожаловать 🎉",
                    KeyboardFactory.mainMenu());
        } else {
            bot.sendText(chatId, "Пользователь не найден.");
        }
    }

    // ================================================================
    // Admin: /block {telegramId}
    // ================================================================

    private void handleBlock(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;

        Long targetId = parseId(text, chatId, bot);
        if (targetId == null) return;

        if (userService.blockUser(targetId)) {
            bot.sendText(chatId, "🚫 Пользователь " + targetId + " заблокирован.");
            bot.sendText(targetId,
                    "Ваш доступ к Monetka был заблокирован администратором.");
        } else {
            bot.sendText(chatId, "Пользователь не найден.");
        }
    }

    // ================================================================
    // Admin: /unblock {telegramId}
    // ================================================================

    private void handleUnblock(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;

        Long targetId = parseId(text, chatId, bot);
        if (targetId == null) return;

        if (userService.unblockUser(targetId)) {
            bot.sendText(chatId, "✅ Пользователь " + targetId + " разблокирован.");
            bot.sendMessage(targetId,
                    "✅ Ваш доступ к Monetka восстановлен!",
                    KeyboardFactory.mainMenu());
        } else {
            bot.sendText(chatId, "Пользователь не найден.");
        }
    }

    // ================================================================
    // Notify admins about new user
    // ================================================================

    private void notifyAdmins(User user, MonetkaBot bot) {
        String msg = "🆕 Новый пользователь:\n" +
                user.getDisplayName() + "\nID: `" + user.getTelegramId() + "`";

        for (Long adminId : botProperties.getAdminIds()) {
            bot.sendMessage(adminId, msg,
                    KeyboardFactory.adminApproveButtons(user.getTelegramId()));
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private boolean checkApproved(long chatId, long telegramId, MonetkaBot bot) {
        if (!userService.isApproved(telegramId)) {
            bot.sendText(chatId, "⏳ Доступ не подтверждён.");
            return false;
        }
        return true;
    }

    private boolean checkAdmin(long chatId, long telegramId, MonetkaBot bot) {
        if (!botProperties.getAdminIds().contains(telegramId)) {
            bot.sendText(chatId, "⛔ Нет доступа.");
            return false;
        }
        return true;
    }

    private Long parseId(String text, long chatId, MonetkaBot bot) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2) {
            bot.sendText(chatId, "Укажи Telegram ID. Пример: " + parts[0] + " 123456789");
            return null;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            bot.sendText(chatId, "Некорректный ID: " + parts[1]);
            return null;
        }
    }

    private String extractCommand(String text) {
        if (text == null) return "";
        // Берём только первое слово (команда может идти с аргументом)
        String first = text.trim().split("\\s+")[0];
        // Убираем @botname если есть
        return first.split("@")[0].toLowerCase();
    }

    private String fmt(BigDecimal amount) {
        return String.format("%,.0f сом", amount);
    }
}