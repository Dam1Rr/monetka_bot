package com.monetka.admin;

import com.monetka.bot.MonetkaBot;
import com.monetka.service.BotSettingsService;
import com.monetka.admin.ActivityStatsService;
import com.monetka.config.BotProperties;
import com.monetka.model.User;
import com.monetka.model.enums.UserState;
import com.monetka.service.OnboardingService;
import com.monetka.service.UserStateService;
import com.monetka.repository.UserRepository;
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
    private final UserStateService      stateService;
    private final UserRepository        userRepository;

    public AdminHandler(AdminService adminService, BotProperties botProperties,
                        UserExportService exportService,
                        OnboardingService onboardingService,
                        BotSettingsService botSettingsService,
                        ActivityStatsService activityStatsService,
                        UserStateService stateService,
                        UserRepository userRepository) {
        this.adminService         = adminService;
        this.botProperties        = botProperties;
        this.exportService        = exportService;
        this.onboardingService    = onboardingService;
        this.botSettingsService   = botSettingsService;
        this.activityStatsService = activityStatsService;
        this.stateService         = stateService;
        this.userRepository       = userRepository;
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
        else if (action.equals("broadcast"))             startBroadcast(chatId, telegramId, bot);
        else if (action.equals("broadcast_confirm"))     confirmBroadcast(chatId, telegramId, bot);
        else if (action.equals("broadcast_cancel"))      cancelBroadcast(chatId, telegramId, bot);
        else log.warn("Unknown admin callback: {}", data);
    }

    // ================================================================
    // Activity stats — live snapshot
    // ================================================================

    private void showActivityStats(long chatId, MonetkaBot bot) {
        ActivityStatsService.ActivitySnapshot s = activityStatsService.getSnapshot();

        long retPct = s.wau() > 0 && s.totalUsers() > 0
                ? s.wau() * 100 / s.totalUsers() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Метрики продукта*\n\n");

        // Users
        sb.append("👥 *Пользователи*\n");
        sb.append("  Всего активных: *").append(s.totalUsers()).append("*");
        if (s.pendingUsers() > 0) sb.append("  _(+").append(s.pendingUsers()).append(" заявок)_");
        sb.append("\n");
        if (s.churnedUsers() > 0)
            sb.append("  🚪 Заблокировали бота: *").append(s.churnedUsers()).append("*\n");
        sb.append("\n");

        // DAU / WAU / MAU
        sb.append("📈 *Активность (DAU / WAU / MAU)*\n");
        sb.append("  Сегодня: *").append(s.dau()).append("*\n");
        sb.append("  За 7 дней: *").append(s.wau()).append("*\n");
        sb.append("  За 30 дней: *").append(s.mau()).append("*\n");
        sb.append("  Retention 7д: *").append(retPct).append("%*  ");
        sb.append(retPct >= 50 ? "✅" : retPct >= 30 ? "⚠️" : "🔴").append("\n");
        sb.append("  Неактивны 3+ дней: *").append(s.inactive3days()).append("*\n\n");

        // Transactions
        sb.append("💳 *Транзакции*\n");
        sb.append("  Сегодня: *").append(s.txToday()).append("*\n");
        sb.append("  За 7 дней: *").append(s.txWeek()).append("*\n");
        sb.append("  За месяц: *").append(s.txMonth()).append("*\n");
        if (s.avgExpensePerUser().compareTo(java.math.BigDecimal.ZERO) > 0)
            sb.append("  Средний расход/юзер: *").append(fmt(s.avgExpensePerUser())).append("*\n");
        sb.append("\n");

        // Top categories this month
        if (!s.topCategories().isEmpty()) {
            sb.append("🔥 *Топ категорий этого месяца (все юзеры):*\n");
            for (int i = 0; i < s.topCategories().size(); i++) {
                ActivityStatsService.CategoryTrend cat = s.topCategories().get(i);
                sb.append("  ").append(i + 1).append(". ")
                        .append(cat.label()).append(" — *").append(fmt(cat.total())).append("*")
                        .append("  (").append(cat.txCount()).append(" трат)\n");
            }
            sb.append("\n");
        }

        // Top users this week
        if (!s.top5().isEmpty()) {
            sb.append("🏆 *Топ юзеров за неделю:*\n");
            for (int i = 0; i < s.top5().size(); i++) {
                sb.append("  ").append(i + 1).append(". ")
                        .append(s.top5().get(i).name())
                        .append(" — ").append(s.top5().get(i).txCount()).append(" зап.\n");
            }
        }

        bot.sendMarkdown(chatId, sb.toString());
        bot.sendMarkdown(chatId, "📋 Полная таблица активности — в выгрузке Excel",
                AdminKeyboardFactory.backToMenu());
    }

    // ================================================================
    // Broadcast
    // ================================================================

    private void startBroadcast(long chatId, long telegramId, MonetkaBot bot) {
        long reachable = userRepository.findAllByStatusAndBlockedBotFalse(
                com.monetka.model.enums.UserStatus.ACTIVE).size();
        stateService.setState(telegramId, UserState.WAITING_BROADCAST_MESSAGE);
        bot.sendMarkdown(chatId,
                "📢 *Рассылка*\n\n" +
                        "Получат сообщение: *" + reachable + "* активных пользователей\n" +
                        "_(заблокировавшие бота исключены)_\n\n" +
                        "Напиши текст рассылки — поддерживается *жирный*, _курсив_, `код`\n\n" +
                        "_Для отмены нажми кнопку ниже ↓_");
        bot.sendMarkdown(chatId, "✏️ Пиши текст:", AdminKeyboardFactory.broadcastCancel());
    }

    public void handleBroadcastInput(long chatId, long telegramId, String text, MonetkaBot bot) {
        stateService.putData(telegramId, "broadcast_text", text);
        stateService.setState(telegramId, UserState.IDLE);

        long reachable = userRepository.findAllByStatusAndBlockedBotFalse(
                com.monetka.model.enums.UserStatus.ACTIVE).size();

        bot.sendMarkdown(chatId,
                "📋 *Предпросмотр рассылки:*\n\n" + text + "\n\n" +
                        "───────────────\n" +
                        "Будет отправлено: *" + reachable + "* пользователям");
        bot.sendMarkdown(chatId, "Отправляем?", AdminKeyboardFactory.broadcastConfirm());
    }

    private void confirmBroadcast(long chatId, long telegramId, MonetkaBot bot) {
        String text = stateService.getData(telegramId, "broadcast_text");
        if (text == null || text.isBlank()) {
            bot.sendMarkdown(chatId, "❌ Текст рассылки не найден. Начни заново.");
            sendMainMenu(chatId, bot);
            return;
        }

        List<com.monetka.model.User> users = userRepository.findAllByStatusAndBlockedBotFalse(
                com.monetka.model.enums.UserStatus.ACTIVE);

        bot.sendMarkdown(chatId, "⏳ Отправляю " + users.size() + " пользователям...");

        int sent = 0, failed = 0;
        for (com.monetka.model.User u : users) {
            try {
                bot.sendMarkdown(u.getTelegramId(), "📢 *Сообщение от Monetka*\n\n" + text);
                sent++;
                Thread.sleep(50); // rate limit protection
            } catch (Exception e) {
                failed++;
                log.warn("Broadcast failed for {}: {}", u.getTelegramId(), e.getMessage());
            }
        }

        stateService.putData(telegramId, "broadcast_text", null);
        bot.sendMarkdown(chatId,
                "✅ *Рассылка завершена*\n\n" +
                        "Отправлено: *" + sent + "*\n" +
                        (failed > 0 ? "Не дошло: *" + failed + "* (заблокировали бота)" : "Все получили! 🎉"),
                AdminKeyboardFactory.backToMenu());
    }

    private void cancelBroadcast(long chatId, long telegramId, MonetkaBot bot) {
        stateService.reset(telegramId);
        bot.sendMarkdown(chatId, "❌ Рассылка отменена.");
        sendMainMenu(chatId, bot);
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
        bot.sendMarkdown(chatId,
                "🛡 *Панель администратора*\n\nВыберите раздел:",
                AdminKeyboardFactory.mainMenu(open));
    }

    // ================================================================
    // 1 — PENDING USERS
    // ================================================================

    private void showPending(long chatId, MonetkaBot bot) {
        List<User> users = adminService.getPendingUsers();

        if (users.isEmpty()) {
            bot.sendMarkdown(chatId, "✅ Заявок нет.", AdminKeyboardFactory.backToMenu());
            return;
        }

        bot.sendMarkdown(chatId, "📥 *Заявки на доступ: " + users.size() + "*");

        for (User u : users) {
            String card = buildUserCard(u);
            bot.sendMarkdown(chatId, card, AdminKeyboardFactory.pendingUserActions(u.getTelegramId()));
        }
    }

    // ================================================================
    // 2 — BLOCKED USERS
    // ================================================================

    private void showBlocked(long chatId, MonetkaBot bot) {
        List<User> users = adminService.getBlockedUsers();

        if (users.isEmpty()) {
            bot.sendMarkdown(chatId, "✅ Заблокированных нет.", AdminKeyboardFactory.backToMenu());
            return;
        }

        bot.sendMarkdown(chatId, "🚫 *Заблокированные: " + users.size() + "*");

        for (User u : users) {
            String card = buildUserCard(u);
            bot.sendMarkdown(chatId, card, AdminKeyboardFactory.blockedUserActions(u.getTelegramId()));
        }
    }

    // ================================================================
    // 3 — ACTIVE USERS
    // ================================================================

    private void showActiveUsers(long chatId, MonetkaBot bot) {
        List<User> users = adminService.getActiveUsers();

        if (users.isEmpty()) {
            bot.sendMarkdown(chatId, "Активных пользователей нет.", AdminKeyboardFactory.backToMenu());
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
        bot.sendMarkdown(chatId, "⬆️ Список выше", AdminKeyboardFactory.backToMenu());
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

        bot.sendMarkdown(chatId, msg, AdminKeyboardFactory.backToMenu());
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

        bot.sendMarkdown(chatId, warning, AdminKeyboardFactory.wipeStep1());
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

        bot.sendMarkdown(chatId, confirm, AdminKeyboardFactory.wipeStep2());
    }

    // ================================================================
    // 5 — WIPE: execute
    // ================================================================

    private void executeWipe(long chatId, long telegramId, MonetkaBot bot) {
        bot.sendMarkdown(chatId, "⏳ *Очищаю базу данных...*");

        try {
            adminService.resetDatabase();
            log.warn("Database wiped by admin {}", telegramId);

            bot.sendMarkdown(chatId,
                    "✅ *База данных успешно сброшена.*\n\n" +
                            "Стандартные категории восстановлены.\n" +
                            "Бот готов к работе.",
                    AdminKeyboardFactory.backToMenu());

        } catch (Exception e) {
            log.error("Database wipe failed: {}", e.getMessage(), e);
            bot.sendMarkdown(chatId,
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