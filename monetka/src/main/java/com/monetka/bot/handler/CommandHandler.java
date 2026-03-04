package com.monetka.bot.handler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.config.BotProperties;
import com.monetka.model.Subscription;
import com.monetka.model.User;
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
        String text       = message.getText();
        String command    = extractCommand(text);
        long   chatId     = message.getChatId();
        long   telegramId = message.getFrom().getId();

        switch (command) {
            case "/start"         -> handleStart(message, chatId, telegramId, bot);
            case "/help"          -> handleHelp(chatId, telegramId, bot);
            case "/cancel"        -> handleCancel(chatId, telegramId, bot);
            case "/balance"       -> handleBalance(chatId, telegramId, bot);
            case "/stats"         -> handleStats(chatId, telegramId, bot);
            case "/subscriptions" -> handleSubscriptions(chatId, telegramId, bot);
            case "/pending"       -> handlePending(chatId, telegramId, bot);
            case "/blocked"       -> handleBlockedList(chatId, telegramId, bot);
            case "/approve"       -> handleApprove(text, chatId, telegramId, bot);
            case "/block"         -> handleBlock(text, chatId, telegramId, bot);
            case "/unblock"       -> handleUnblock(text, chatId, telegramId, bot);
            default               -> bot.sendText(chatId, "Неизвестная команда. Напиши /help");
        }
    }

    // ================================================================
    // /start
    // ================================================================

    private void handleStart(Message message, long chatId, long telegramId, MonetkaBot bot) {
        org.telegram.telegrambots.meta.api.objects.User from = message.getFrom();

        User user = userService.registerOrGet(
                telegramId, from.getUserName(), from.getFirstName(), from.getLastName()
        );
        stateService.reset(telegramId);

        switch (user.getStatus()) {
            case PENDING -> {
                bot.sendText(chatId,
                        "👋 Привет, " + user.getDisplayName() + "!\n\n" +
                                "Заявка отправлена администратору. Ожидай ⏳");
                notifyAdmins(user, bot);
            }
            case APPROVED -> bot.sendMessage(chatId,
                    "👋 Снова привет, " + user.getDisplayName() + "! Готов к работе 💪",
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
                        "*Добавить расход / доход:*\n" +
                        "Нажми кнопку → введи `название сумма`\n" +
                        "Пример: `шаурма 300`\n\n" +
                        "*Команды:*\n" +
                        "/balance — текущий баланс\n" +
                        "/stats — статистика\n" +
                        "/subscriptions — подписки\n" +
                        "/cancel — отменить действие\n" +
                        "/help — справка"
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
                        "💳 *Баланс:*\n\n*" + fmt(user.getBalance()) + "*")
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
    // Admin: /pending — список с кнопками на каждого
    // ================================================================

    private void handlePending(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;

        List<User> pending = userService.getPendingUsers();
        if (pending.isEmpty()) {
            bot.sendText(chatId, "✅ Заявок нет.");
            return;
        }

        bot.sendMarkdown(chatId, "⏳ *Ожидают одобрения: " + pending.size() + "*");

        // Каждый пользователь — отдельное сообщение с кнопками
        for (User u : pending) {
            String info = "👤 *" + u.getDisplayName() + "*\n" +
                    "🆔 `" + u.getTelegramId() + "`";
            bot.sendMessage(chatId, info,
                    KeyboardFactory.adminUserActions(u.getTelegramId(), false));
        }
    }

    // ================================================================
    // Admin: /blocked — список с кнопками разблокировки
    // ================================================================

    private void handleBlockedList(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;

        List<User> blocked = userService.getBlockedUsers();
        if (blocked.isEmpty()) {
            bot.sendText(chatId, "✅ Заблокированных нет.");
            return;
        }

        bot.sendMarkdown(chatId, "🚫 *Заблокированы: " + blocked.size() + "*");

        for (User u : blocked) {
            String info = "👤 *" + u.getDisplayName() + "*\n" +
                    "🆔 `" + u.getTelegramId() + "`";
            bot.sendMessage(chatId, info,
                    KeyboardFactory.adminUserActions(u.getTelegramId(), true));
        }
    }

    // ================================================================
    // Admin: /approve /block /unblock
    // ================================================================

    private void handleApprove(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long targetId = parseId(text, chatId, bot);
        if (targetId == null) return;

        if (userService.approveUser(targetId)) {
            bot.sendText(chatId, "✅ Пользователь " + targetId + " одобрен.");
            bot.sendMessage(targetId,
                    "✅ *Добро пожаловать в Monetka!*\nДоступ подтверждён 🎉",
                    KeyboardFactory.mainMenu());
        } else {
            bot.sendText(chatId, "Пользователь не найден.");
        }
    }

    private void handleBlock(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long targetId = parseId(text, chatId, bot);
        if (targetId == null) return;

        if (userService.blockUser(targetId)) {
            bot.sendText(chatId, "🚫 Пользователь " + targetId + " заблокирован.");
            bot.sendText(targetId, "Ваш доступ к Monetka был заблокирован администратором.");
        } else {
            bot.sendText(chatId, "Пользователь не найден.");
        }
    }

    private void handleUnblock(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long targetId = parseId(text, chatId, bot);
        if (targetId == null) return;

        if (userService.unblockUser(targetId)) {
            bot.sendText(chatId, "✅ Пользователь " + targetId + " разблокирован.");
            bot.sendMessage(targetId,
                    "✅ Твой доступ к Monetka восстановлен!",
                    KeyboardFactory.mainMenu());
        } else {
            bot.sendText(chatId, "Пользователь не найден.");
        }
    }

    // ================================================================
    // Уведомление админам о новом пользователе
    // ================================================================

    private void notifyAdmins(User user, MonetkaBot bot) {
        String msg = "🆕 *Новая заявка*\n\n" +
                "👤 " + user.getDisplayName() + "\n" +
                "🆔 `" + user.getTelegramId() + "`";

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
            bot.sendText(chatId, "Укажи ID. Пример: " + parts[0] + " 123456789");
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
        return text.trim().split("\\s+")[0].split("@")[0].toLowerCase();
    }

    private String fmt(BigDecimal amount) {
        return String.format("%,.0f сом", amount);
    }
}