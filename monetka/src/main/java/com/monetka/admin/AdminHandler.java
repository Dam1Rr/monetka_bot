package com.monetka.admin;

import com.monetka.bot.MonetkaBot;
import com.monetka.service.BotSettingsService;
import com.monetka.admin.ActivityStatsService;
import com.monetka.config.BotProperties;
import com.monetka.model.User;
import com.monetka.service.OnboardingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private final AdminService      adminService;
    private final BotProperties     botProperties;
    private final UserExportService exportService;

    private final OnboardingService onboardingService;
    private final BotSettingsService   botSettingsService;
    private final ActivityStatsService  activityStatsService;

    public AdminHandler(AdminService adminService, BotProperties botProperties,
                        UserExportService exportService,
                        OnboardingService onboardingService,
                        BotSettingsService botSettingsService,
                        ActivityStatsService activityStatsService) {
        this.adminService      = adminService;
        this.botProperties     = botProperties;
        this.exportService     = exportService;
        this.onboardingService  = onboardingService;
        this.botSettingsService  = botSettingsService;
        this.activityStatsService = activityStatsService;
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
        else if (action.equals("export"))            exportUsers(chatId, bot);
        else if (action.startsWith("approve:"))      approveUser(action, chatId, bot);
        else if (action.startsWith("reject:"))       rejectUser(action, chatId, bot);
        else if (action.startsWith("block:"))        blockUser(action, chatId, bot);
        else if (action.startsWith("unblock:"))      unblockUser(action, chatId, bot);
        else if (action.equals("toggle_reg"))         toggleRegistration(chatId, bot);
        else if (action.equals("activity"))              showActivityStats(chatId, bot);
        else log.warn("Unknown admin callback: {}", data);
    }

    // ================================================================
    // Activity stats — live snapshot
    // ================================================================

    private void showActivityStats(long chatId, MonetkaBot bot) {
        ActivityStatsService.ActivitySnapshot s = activityStatsService.getSnapshot();

        long retPct = s.totalUsers() > 0 ? s.activeWeek() * 100 / s.totalUsers() : 0;
        String retIcon = retPct >= 50 ? "\u2705" : "\u26A0\uFE0F";

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCCA *\u0410\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c \u2014 \u0441\u0435\u0439\u0447\u0430\u0441*\n\n");

        sb.append("\uD83D\uDC65 \u041f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u0435\u0439: *").append(s.totalUsers()).append("*");
        if (s.pendingUsers() > 0) sb.append(" (+").append(s.pendingUsers()).append(" \u0437\u0430\u044f\u0432\u043e\u043a)");
        sb.append("\n");

        sb.append("\u2705 \u0410\u043a\u0442\u0438\u0432\u043d\u044b \u0441\u0435\u0433\u043e\u0434\u043d\u044f: *").append(s.activeToday()).append("*\n");
        sb.append("\uD83D\uDCC5 \u0410\u043a\u0442\u0438\u0432\u043d\u044b \u0437\u0430 7 \u0434\u043d\u0435\u0439: *").append(s.activeWeek()).append("*\n");
        sb.append("\uD83D\uDE34 \u041d\u0435\u0430\u043a\u0442\u0438\u0432\u043d\u044b 3+ \u0434\u043d\u044f: *").append(s.inactive3days()).append("*\n\n");

        sb.append("\uD83D\uDCB8 \u0422\u0440\u0430\u043d\u0437\u0430\u043a\u0446\u0438\u0439 \u0441\u0435\u0433\u043e\u0434\u043d\u044f: *").append(s.txToday()).append("*\n");
        sb.append("\uD83D\uDCC8 \u0422\u0440\u0430\u043d\u0437\u0430\u043a\u0446\u0438\u0439 \u0437\u0430 7 \u0434\u043d\u0435\u0439: *").append(s.txWeek()).append("*\n\n");

        sb.append(retIcon).append(" *Retention 7 \u0434\u043d\u0435\u0439: ").append(retPct).append("%*\n\n");

        if (!s.top5().isEmpty()) {
            sb.append("\uD83D\uDD25 *\u0422\u043e\u043f \u0437\u0430 \u043d\u0435\u0434\u0435\u043b\u044e:*\n");
            for (int i = 0; i < s.top5().size(); i++) {
                sb.append(i + 1).append(". ").append(s.top5().get(i).name())
                        .append(" \u2014 ").append(s.top5().get(i).txCount()).append(" \u0437\u0430\u043f.\n");
            }
        }

        bot.sendMarkdown(chatId, sb.toString());
        bot.sendMessage(chatId, "\uD83D\uDCCB \u041F\u043E\u043B\u043D\u0430\u044F \u0442\u0430\u0431\u043B\u0438\u0446\u0430 \u0430\u043A\u0442\u0438\u0432\u043D\u043E\u0441\u0442\u0438 \u2014 \u0432 \u0432\u044B\u0433\u0440\u0443\u0437\u043A\u0435 Excel \u0432\u043A\u043B\u0430\u0434\u043A\u0435 \u00AB\u0410\u043A\u0442\u0438\u0432\u043D\u043E\u0441\u0442\u044C\u00BB",
                AdminKeyboardFactory.backToMenu());
    }

    // ================================================================
    // Registration mode toggle
    // ================================================================

    private void toggleRegistration(long chatId, MonetkaBot bot) {
        boolean nowOpen = !botSettingsService.isRegistrationOpen();
        botSettingsService.setRegistrationOpen(nowOpen);
        String msg = nowOpen
                ? "🟢 *Регистрация открыта*\n\nТеперь все могут пользоваться ботом без одобрения."
                : "🔴 *Регистрация по заявкам*\n\nНовые пользователи ждут одобрения администратора.";
        bot.sendMarkdown(chatId, msg);
        sendMainMenu(chatId, bot);
    }

    // ================================================================
    // Main menu
    // ================================================================

    private void sendMainMenu(long chatId, MonetkaBot bot) {
        boolean open = botSettingsService.isRegistrationOpen();
        bot.sendMessage(chatId,
                "🛡 *Панель администратора*\n\nВыберите раздел:",
                AdminKeyboardFactory.mainMenu(open));
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
            sb.append(i + 1).append(". *").append(escapeMarkdown(u.getDisplayName())).append("*");
            if (u.getUsername() != null && !u.getUsername().isBlank()) {
                sb.append(" @").append(u.getUsername());
            }
            sb.append(" `").append(u.getTelegramId()).append("`\n");
        }
        bot.sendMarkdown(chatId, sb.toString());
        bot.sendMessage(chatId, "⬆️ Список выше", AdminKeyboardFactory.backToMenu());
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
    // ================================================================
    // 5 — EXPORT
    // ================================================================

    private void exportUsers(long chatId, MonetkaBot bot) {
        try {
            bot.sendMarkdown(chatId, "⏳ Генерирую файл...");
            byte[] xlsx = exportService.generateUsersXlsx();
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            String filename = "monetka_users_" + date + ".xlsx";
            String caption = "📊 *Список пользователей Monetka*\n" +
                    "_Выгрузка от " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "_";
            bot.sendDocument(chatId, xlsx, filename, caption);
        } catch (IOException e) {
            bot.sendMarkdown(chatId, "❌ Ошибка при генерации файла: " + e.getMessage());
        }
    }

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
            // Start onboarding flow
            onboardingService.sendWelcome(u, targetId, bot);
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