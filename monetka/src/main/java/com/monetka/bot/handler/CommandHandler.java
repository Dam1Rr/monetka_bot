package com.monetka.bot.handler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.config.BotProperties;
import com.monetka.model.User;
import com.monetka.model.enums.UserStatus;
import com.monetka.service.UserService;
import com.monetka.service.UserStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandHandler {

    private final UserService      userService;
    private final UserStateService stateService;
    private final BotProperties    botProperties;

    public void handle(Message message, MonetkaBot bot) {
        String command   = extractCommand(message.getText());
        long   chatId    = message.getChatId();
        long   telegramId = message.getFrom().getId();

        switch (command) {
            case "/start"  -> handleStart(message, chatId, telegramId, bot);
            case "/help"   -> handleHelp(chatId, telegramId, bot);
            case "/cancel" -> handleCancel(chatId, telegramId, bot);
            case "/users"  -> handleUsers(chatId, telegramId, bot);
            default -> {
                if (command.startsWith("/approve")) handleApprove(message, chatId, telegramId, bot);
                else if (command.startsWith("/block")) handleBlock(message, chatId, telegramId, bot);
                else bot.sendText(chatId, "Неизвестная команда. Напиши /help");
            }
        }
    }

    // ---- /start ----

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
                    "Твоя заявка на доступ отправлена администратору.\n" +
                    "Ожидай подтверждения 🕐");
                notifyAdmins(user, bot);
            }
            case ACTIVE -> bot.sendMessage(chatId,
                "👋 Снова привет, " + user.getDisplayName() + "!\n" +
                "Я готов к работе 💪",
                KeyboardFactory.mainMenu());
            case BLOCKED -> bot.sendText(chatId,
                "⛔ Ваш доступ заблокирован. Обратитесь к администратору.");
        }
    }

    // ---- /help ----

    private void handleHelp(long chatId, long telegramId, MonetkaBot bot) {
        if (!userService.isActive(telegramId)) {
            bot.sendText(chatId, "Доступ не подтверждён.");
            return;
        }
        bot.sendMarkdown(chatId, """
            *Monetka — финансовый помощник* 💰
            
            *Расходы и доходы:*
            Нажми кнопку и введи в формате:
            `шаурма 300`
            `зарплата 150000`
            
            *Команды:*
            /start — главное меню
            /cancel — отмена текущего действия
            /help — эта справка
            """);
    }

    // ---- /cancel ----

    private void handleCancel(long chatId, long telegramId, MonetkaBot bot) {
        stateService.reset(telegramId);
        bot.sendMessage(chatId, "Действие отменено.", KeyboardFactory.mainMenu());
    }

    // ---- Admin: /users ----

    private void handleUsers(long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) { bot.sendText(chatId, "Нет доступа."); return; }

        List<User> pending = userService.getPendingUsers();
        List<User> active  = userService.getActiveUsers();

        StringBuilder sb = new StringBuilder("👥 *Пользователи*\n\n");
        sb.append("⏳ Ожидают: ").append(pending.size()).append("\n");
        pending.forEach(u -> sb.append("  • ").append(u.getDisplayName())
            .append(" (").append(u.getTelegramId()).append(")\n"));

        sb.append("\n✅ Активные: ").append(active.size()).append("\n");
        active.forEach(u -> sb.append("  • ").append(u.getDisplayName())
            .append(" (").append(u.getTelegramId()).append(")\n"));

        bot.sendMarkdown(chatId, sb.toString());
    }

    // ---- Admin: /approve {id} ----

    private void handleApprove(Message message, long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) { bot.sendText(chatId, "Нет доступа."); return; }

        String[] parts = message.getText().split(" ");
        if (parts.length < 2) { bot.sendText(chatId, "Формат: /approve {telegramId}"); return; }

        try {
            long targetId = Long.parseLong(parts[1]);
            if (userService.approveUser(targetId)) {
                bot.sendText(chatId, "✅ Пользователь " + targetId + " одобрен.");
                bot.sendMessage(targetId,
                    "✅ Ваш доступ к Monetka подтверждён! Добро пожаловать 🎉",
                    KeyboardFactory.mainMenu());
            } else {
                bot.sendText(chatId, "Пользователь не найден.");
            }
        } catch (NumberFormatException e) {
            bot.sendText(chatId, "Некорректный ID.");
        }
    }

    // ---- Admin: /block {id} ----

    private void handleBlock(Message message, long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) { bot.sendText(chatId, "Нет доступа."); return; }

        String[] parts = message.getText().split(" ");
        if (parts.length < 2) { bot.sendText(chatId, "Формат: /block {telegramId}"); return; }

        try {
            long targetId = Long.parseLong(parts[1]);
            if (userService.blockUser(targetId)) {
                bot.sendText(chatId, "🚫 Пользователь " + targetId + " заблокирован.");
                bot.sendText(targetId, "⛔ Ваш доступ заблокирован администратором.");
            } else {
                bot.sendText(chatId, "Пользователь не найден.");
            }
        } catch (NumberFormatException e) {
            bot.sendText(chatId, "Некорректный ID.");
        }
    }

    // ---- Notify admins about new registration ----

    private void notifyAdmins(User user, MonetkaBot bot) {
        String msg = "🆕 Новый пользователь:\n" +
            user.getDisplayName() + " (ID: " + user.getTelegramId() + ")";

        for (Long adminId : botProperties.getAdminIds()) {
            bot.sendMessage(adminId, msg,
                KeyboardFactory.adminApproveButtons(user.getTelegramId()));
        }
    }

    // ---- Helpers ----

    private boolean isAdmin(long telegramId) {
        return botProperties.getAdminIds().contains(telegramId);
    }

    private String extractCommand(String text) {
        if (text == null) return "";
        String[] parts = text.split("@");
        return parts[0].toLowerCase().trim();
    }
}
