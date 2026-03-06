package com.monetka.admin;

import com.monetka.bot.MonetkaBot;
import com.monetka.config.BotProperties;
import com.monetka.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Handles /admin command and all adm:* callback queries.
 *
 * Entry points (called from CommandHandler and CallbackHandler):
 *   handleCommand(Message, MonetkaBot)     ← /admin
 *   handleCallback(CallbackQuery, MonetkaBot) ← adm:*
 */
@Component
public class AdminHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminHandler.class);

    private final AdminService   adminService;
    private final BotProperties  botProperties;

    public AdminHandler(AdminService adminService, BotProperties botProperties) {
        this.adminService  = adminService;
        this.botProperties = botProperties;
    }

    // ================================================================
    // /admin command
    // ================================================================

    public void handleCommand(Message message, MonetkaBot bot) {
        long chatId     = message.getChatId();
        long telegramId = message.getFrom().getId();

        if (!isAdmin(telegramId)) {
            bot.sendText(chatId, "⛔ Нет доступа.");
            return;
        }

        sendMainMenu(chatId, bot);
    }

    // ================================================================
    // Callback router  (adm:<action>[:<param>])
    // ================================================================

    public void handleCallback(CallbackQuery callback, MonetkaBot bot) {
        long   chatId     = callback.getMessage().getChatId();
        long   telegramId = callback.getFrom().getId();
        String data       = callback.getData();

        ack(callback.getId(), bot);

        if (!isAdmin(telegramId)) {
            bot.sendText(chatId, "⛔ Нет доступа.");
            return;
        }

        String action = data.substring(4); // strip "adm:"

        if      (action.equals("menu"))              sendMainMenu(chatId, bot);
        else if (action.equals("pending"))           showPending(chatId, bot);
        else if (action.equals("blocked"))           showBlocked(chatId, bot);
        else if (action.equals("users"))             showActiveUsers(chatId, bot);
        else if (action.equals("stats"))             showStats(chatId, bot);
        else if (action.equals("wipe_1"))            showWipeStep1(chatId, bot);
        else if (action.equals("wipe_2"))            showWipeStep2(chatId, bot);
        else if (action.equals("wipe_cancel"))       sendMainMenu(chatId, bot);
        else if (action.equals("wipe_exec"))         executeWipe(chatId, telegramId, bot);
        else if (action.startsWith("approve:"))      approveUser(action, chatId, bot);
        else if (action.startsWith("reject:"))       rejectUser(action, chatId, bot);
        else if (action.startsWith("block:"))        blockUser(action, chatId, bot);
        else if (action.startsWith("unblock:"))      unblockUser(action, chatId, bot);
        else log.warn("Unknown admin callback: {}", data);
    }

    // ================================================================
    // Main menu
    // ================================================================

    private void sendMainMenu(long chatId, MonetkaBot bot) {
        bot.sendMessage(chatId,
                "🛡 *Панель администратора*\n\nВыберите раздел:",
                AdminKeyboardFactory.mainMenu());
    }

    // ================================================================
    // 1 — PENDING USERS
    // ================================================================

    private void showPending(long chatId, MonetkaBot bot) {
        List<User> users = adminService.getPendingUsers();

        if (users.isEmpty()) {
            bot.sendMessage(chatId, "✅ Заявок нет.", AdminKeyboardFactory.backToMenu());
            return;
        }

        bot.sendMarkdown(chatId, "📥 *Заявки на доступ: " + users.size() + "*");

        for (User u : users) {
            String card = buildUserCard(u);
            bot.sendMessage(chatId, card, AdminKeyboardFactory.pendingUserActions(u.getTelegramId()));
        }
    }

    // ================================================================
    // 2 — BLOCKED USERS
    // ================================================================

    private void showBlocked(long chatId, MonetkaBot bot) {
        List<User> users = adminService.getBlockedUsers();

        if (users.isEmpty()) {
            bot.sendMessage(chatId, "✅ Заблокированных нет.", AdminKeyboardFactory.backToMenu());
            return;
        }

        bot.sendMarkdown(chatId, "🚫 *Заблокированные: " + users.size() + "*");

        for (User u : users) {
            String card = buildUserCard(u);
            bot.sendMessage(chatId, card, AdminKeyboardFactory.blockedUserActions(u.getTelegramId()));
        }
    }

    // ================================================================
    // 3 — ACTIVE USERS
    // ================================================================

    private void showActiveUsers(long chatId, MonetkaBot bot) {
        List<User> users = adminService.getActiveUsers();

        if (users.isEmpty()) {
            bot.sendMessage(chatId, "Активных пользователей нет.", AdminKeyboardFactory.backToMenu());
            return;
        }

        // Single summary message with numbered list
        StringBuilder sb = new StringBuilder();
        sb.append("👥 *Активные пользователи: ").append(users.size()).append("*\n\n");
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            sb.append("*").append(i + 1).append(".* ");
            sb.append("*").append(escapeMarkdown(u.getDisplayName())).append("*\n");
            if (u.getUsername() != null && !u.getUsername().isBlank()) {
                sb.append("   🔗 @").append(u.getUsername()).append("\n");
            }
            sb.append("   🆔 `").append(u.getTelegramId()).append("`\n\n");
        }
        bot.sendMarkdown(chatId, sb.toString());

        // Individual cards with block button
        for (User u : users) {
            String card = buildUserCard(u);
            bot.sendMessage(chatId, card, AdminKeyboardFactory.activeUserActions(u.getTelegramId()));
        }
    }

    // ================================================================
    // 4 — STATISTICS
    // ================================================================

    private void showStats(long chatId, MonetkaBot bot) {
        AdminStats s = adminService.getStats();

        String msg = "📊 *Статистика бота*\n\n" +
                "👤 Всего пользователей: *" + s.getTotalUsers()   + "*\n" +
                "✅ Активных:            *" + s.getActiveUsers()  + "*\n" +
                "⏳ Ожидают одобрения:   *" + s.getPendingUsers() + "*\n" +
                "🚫 Заблокированных:     *" + s.getBlockedUsers() + "*\n\n" +
                "💳 Транзакций всего:    *" + s.getTotalTransactions()  + "*\n" +
                "📦 Подписок активных:   *" + s.getTotalSubscriptions() + "*\n" +
                "💸 Сумма расходов:      *" + fmt(s.getTotalExpenses()) + "*";

        bot.sendMessage(chatId, msg, AdminKeyboardFactory.backToMenu());
    }

    // ================================================================
    // 5 — WIPE: step 1
    // ================================================================

    private void showWipeStep1(long chatId, MonetkaBot bot) {
        String warning =
                "⚠️ *ВНИМАНИЕ!*\n\n" +
                        "Вы собираетесь удалить *ВСЕ данные* бота:\n\n" +
                        "• Пользователи\n" +
                        "• Транзакции\n" +
                        "• Подписки\n" +
                        "• Категории\n" +
                        "• Обученные ключевые слова\n\n" +
                        "‼️ *Это действие необратимо.*";

        bot.sendMessage(chatId, warning, AdminKeyboardFactory.wipeStep1());
    }

    // ================================================================
    // 5 — WIPE: step 2
    // ================================================================

    private void showWipeStep2(long chatId, MonetkaBot bot) {
        String confirm =
                "🔥 *Вы абсолютно уверены?*\n\n" +
                        "После нажатия кнопки *«УДАЛИТЬ ВСЁ»*\n" +
                        "база данных будет очищена без возможности восстановления.\n\n" +
                        "_Последний шанс отменить._";

        bot.sendMessage(chatId, confirm, AdminKeyboardFactory.wipeStep2());
    }

    // ================================================================
    // 5 — WIPE: execute
    // ================================================================

    private void executeWipe(long chatId, long telegramId, MonetkaBot bot) {
        bot.sendMarkdown(chatId, "⏳ *Очищаю базу данных...*");

        try {
            adminService.resetDatabase();
            log.warn("Database wiped by admin {}", telegramId);

            bot.sendMessage(chatId,
                    "✅ *База данных успешно сброшена.*\n\n" +
                            "Стандартные категории восстановлены.\n" +
                            "Бот готов к работе.",
                    AdminKeyboardFactory.backToMenu());

        } catch (Exception e) {
            log.error("Database wipe failed: {}", e.getMessage(), e);
            bot.sendMessage(chatId,
                    "❌ *Ошибка при очистке базы данных*\n\n`" + e.getMessage() + "`",
                    AdminKeyboardFactory.backToMenu());
        }
    }

    // ================================================================
    // User action callbacks
    // ================================================================

    private void approveUser(String action, long chatId, MonetkaBot bot) {
        Long targetId = parseId(action);
        Optional<User> result = adminService.approveUser(targetId);

        if (result.isPresent()) {
            User u = result.get();
            bot.sendMarkdown(chatId, "✅ Пользователь *" + u.getDisplayName() + "* одобрен.");
            // Notify the user
            bot.sendMessage(targetId,
                    "✅ *Добро пожаловать в Monetka!*\nВаш доступ подтверждён администратором 🎉",
                    com.monetka.bot.keyboard.KeyboardFactory.mainMenu());
        } else {
            bot.sendText(chatId, "Пользователь не найден.");
        }
    }

    private void rejectUser(String action, long chatId, MonetkaBot bot) {
        Long targetId = parseId(action);
        Optional<User> result = adminService.rejectUser(targetId);

        if (result.isPresent()) {
            User u = result.get();
            bot.sendMarkdown(chatId, "❌ Пользователь *" + u.getDisplayName() + "* отклонён.");
            bot.sendText(targetId, "К сожалению, ваша заявка на доступ к Monetka была отклонена.");
        } else {
            bot.sendText(chatId, "Пользователь не найден.");
        }
    }

    private void blockUser(String action, long chatId, MonetkaBot bot) {
        Long targetId = parseId(action);
        Optional<User> result = adminService.blockUser(targetId);

        if (result.isPresent()) {
            User u = result.get();
            bot.sendMarkdown(chatId, "🚫 Пользователь *" + u.getDisplayName() + "* заблокирован.");
            bot.sendText(targetId, "Ваш доступ к Monetka был заблокирован администратором.");
        } else {
            bot.sendText(chatId, "Пользователь не найден.");
        }
    }

    private void unblockUser(String action, long chatId, MonetkaBot bot) {
        Long targetId = parseId(action);
        Optional<User> result = adminService.unblockUser(targetId);

        if (result.isPresent()) {
            User u = result.get();
            bot.sendMarkdown(chatId, "🔓 Пользователь *" + u.getDisplayName() + "* разблокирован.");
            bot.sendMessage(targetId,
                    "✅ Ваш доступ к Monetka восстановлён!",
                    com.monetka.bot.keyboard.KeyboardFactory.mainMenu());
        } else {
            bot.sendText(chatId, "Пользователь не найден.");
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    public boolean isAdmin(long telegramId) {
        return botProperties.getAdminIds().contains(telegramId);
    }

    private String buildUserCard(User u) {
        StringBuilder sb = new StringBuilder();
        sb.append("👤 *").append(escapeMarkdown(u.getDisplayName())).append("*\n");
        if (u.getUsername() != null && !u.getUsername().isBlank()) {
            sb.append("🔗 @").append(u.getUsername()).append("\n");
        }
        sb.append("🆔 `").append(u.getTelegramId()).append("`");
        return sb.toString();
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_").replace("*", "\\*").replace("`", "\\`");
    }

    private Long parseId(String action) {
        // action = "approve:123456789"
        return Long.parseLong(action.split(":")[1]);
    }

    private String fmt(BigDecimal amount) {
        return String.format("%,.0f сом", amount);
    }

    private void ack(String callbackId, MonetkaBot bot) {
        try {
            bot.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        } catch (TelegramApiException e) {
            log.warn("Failed to ack callback: {}", e.getMessage());
        }
    }
}