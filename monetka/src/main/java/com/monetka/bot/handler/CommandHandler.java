package com.monetka.bot.handler;

import com.monetka.admin.AdminHandler;
import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.config.BotProperties;
import com.monetka.model.Subscription;
import com.monetka.model.User;
import com.monetka.model.enums.UserStatus;
import com.monetka.service.*;
import com.monetka.service.BotSettingsService;
import com.monetka.service.PaydayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Component
public class CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);
    private static final Random RND = new Random();

    private final UserService         userService;
    private final UserStateService    stateService;
    private final ReportService       reportService;
    private final SubscriptionService subscriptionService;
    private final BotProperties       botProperties;
    private final AdminHandler        adminHandler;
    private final PaydayService       paydayService;
    private final BotSettingsService  botSettingsService;

    public CommandHandler(UserService userService, UserStateService stateService,
                          ReportService reportService, SubscriptionService subscriptionService,
                          BotProperties botProperties, AdminHandler adminHandler,
                          PaydayService paydayService,
                          BotSettingsService botSettingsService) {
        this.userService         = userService;
        this.stateService        = stateService;
        this.reportService       = reportService;
        this.subscriptionService = subscriptionService;
        this.botProperties       = botProperties;
        this.adminHandler        = adminHandler;
        this.paydayService       = paydayService;
        this.botSettingsService  = botSettingsService;
    }

    public void handle(Message message, MonetkaBot bot) {
        String command    = extractCommand(message.getText());
        long   chatId     = message.getChatId();
        long   telegramId = message.getFrom().getId();

        switch (command) {
            case "/start"         -> handleStart(message, chatId, telegramId, bot);
            case "/help"          -> handleHelp(chatId, telegramId, bot);
            case "/cancel"        -> handleCancel(chatId, telegramId, bot);
            case "/balance"       -> handleBalance(chatId, telegramId, bot);
            case "/stats"         -> handleStats(chatId, telegramId, bot);
            case "/day"           -> handleDay(chatId, telegramId, bot);
            case "/subscriptions" -> handleSubscriptions(chatId, telegramId, bot);
            case "/admin"         -> adminHandler.handleCommand(message, bot);
            case "/pending"       -> handlePending(chatId, telegramId, bot);
            case "/blocked"       -> handleBlockedList(chatId, telegramId, bot);
            case "/approve"       -> handleApprove(message.getText(), chatId, telegramId, bot);
            case "/block"         -> handleBlock(message.getText(), chatId, telegramId, bot);
            case "/unblock"       -> handleUnblock(message.getText(), chatId, telegramId, bot);
            default               -> bot.sendText(chatId, "Не знаю такой команды 🤷 Попробуй /help");
        }
    }

    // ================================================================
    // /start
    // ================================================================

    private void handleStart(Message message, long chatId, long telegramId, MonetkaBot bot) {
        org.telegram.telegrambots.meta.api.objects.User from = message.getFrom();
        User user = userService.registerOrGet(telegramId, from.getUserName(), from.getFirstName(), from.getLastName());
        stateService.reset(telegramId);

        switch (user.getStatus()) {
            case PENDING -> {
                if (botSettingsService.isRegistrationOpen()) {
                    // Auto-approve — open registration mode
                    userService.approveUser(telegramId);
                    notifyAdminsNewUser(user, bot);
                    onboardingService.sendWelcome(user, chatId, bot);
                } else {
                    // Invite-only — wait for manual approval
                    bot.sendText(chatId,
                            "👋 Привет, " + user.getDisplayName() + "!\n\n" +
                                    "Заявка отправлена администратору 📨\n" +
                                    "Как только одобрят — сразу напишу! ⏳");
                    notifyAdmins(user, bot);
                }
            }
            case ACTIVE -> bot.sendMessage(chatId,
                    pick("👋 Привет, " + user.getDisplayName() + "! Готов считать твои деньги 💪",
                            "С возвращением, " + user.getDisplayName() + "! 🚀 Что записываем?",
                            "Привет-привет, " + user.getDisplayName() + "! 💰 Поехали!"),
                    KeyboardFactory.mainMenu());
            case BLOCKED -> bot.sendText(chatId,
                    "😔 Извини, твой доступ заблокирован администратором.");
        }
    }

    // ================================================================
    // /help
    // ================================================================

    private void handleHelp(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        String adminHint = adminHandler.isAdmin(telegramId) ? "\n\n🛡 *Ты админ:* /admin" : "";
        bot.sendMarkdown(chatId,
                "*Monetka — твой личный финансист* 💰🤖\n\n" +
                        "*Как записать расход:*\n" +
                        "Нажми *💸 Расход* → введи `название сумма`\n" +
                        "Например: `шаурма 300` или `такси 500`\n\n" +
                        "*Как записать доход:*\n" +
                        "Нажми *💰 Доход* → введи `название сумма`\n\n" +
                        "*Команды:*\n" +
                        "/balance — текущий баланс 💳\n" +
                        "/stats — статистика расходов 📊\n" +
                        "/subscriptions — мои подписки 🔔\n" +
                        "/cancel — отменить действие ❌\n" +
                        "/help — эта справка 📖\n\n" +
                        "💡 _Бот учится: чем больше используешь, тем точнее определяет категории!_" +
                        adminHint);
    }

    // ================================================================
    // /cancel
    // ================================================================

    private void handleCancel(long chatId, long telegramId, MonetkaBot bot) {
        stateService.reset(telegramId);
        bot.sendMessage(chatId,
                pick("Ладно, отменяю 👌 Что дальше?",
                        "Отменено ✅ Чем могу помочь?",
                        "Окей, сброс 🔄 Что делаем?"),
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // /balance
    // ================================================================

    private void handleBalance(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            BigDecimal bal = user.getBalance();
            String emoji = bal.compareTo(BigDecimal.ZERO) >= 0 ? "💚" : "🔴";
            String comment = bal.compareTo(BigDecimal.ZERO) > 0
                    ? " — неплохо! 👍"
                    : bal.compareTo(BigDecimal.ZERO) == 0
                    ? " — ноль 😐"
                    : " — в минусе, осторожно! ⚠️";
            bot.sendMarkdown(chatId,
                    "💳 *Твой баланс:*\n\n" + emoji + " *" + fmt(bal) + "*" + comment);
        });
    }

    // ================================================================
    // /stats
    // ================================================================

    private void handleStats(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user ->
                bot.sendMessage(chatId, reportService.buildMonthStats(user), KeyboardFactory.statsPeriod()));
    }

    // ================================================================
    // /subscriptions
    // ================================================================

    private void handleSubscriptions(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            List<Subscription> subs = subscriptionService.getActiveSubscriptions(user);
            bot.sendMessage(chatId, reportService.buildSubscriptionsList(user),
                    KeyboardFactory.subscriptionActions(subs));
        });
    }

    // ================================================================
    // Legacy admin text commands
    // ================================================================

    private void handlePending(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        List<User> pending = userService.getPendingUsers();
        if (pending.isEmpty()) { bot.sendText(chatId, "✅ Заявок нет."); return; }
        bot.sendMarkdown(chatId, "⏳ *Ожидают: " + pending.size() + "*");
        for (User u : pending) {
            bot.sendMessage(chatId,
                    "👤 *" + u.getDisplayName() + "*\n🆔 `" + u.getTelegramId() + "`",
                    KeyboardFactory.pendingUserButtons(u.getTelegramId()));
        }
    }

    private void handleBlockedList(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        List<User> blocked = userService.getBlockedUsers();
        if (blocked.isEmpty()) { bot.sendText(chatId, "✅ Заблокированных нет."); return; }
        bot.sendMarkdown(chatId, "🚫 *Заблокированы: " + blocked.size() + "*");
        for (User u : blocked) {
            bot.sendMessage(chatId,
                    "👤 *" + u.getDisplayName() + "*\n🆔 `" + u.getTelegramId() + "`",
                    KeyboardFactory.blockedUserButtons(u.getTelegramId()));
        }
    }

    private void handleApprove(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long targetId = parseId(text, chatId, bot);
        if (targetId == null) return;
        if (userService.approveUser(targetId)) {
            bot.sendText(chatId, "✅ Пользователь " + targetId + " одобрен.");
            bot.sendMessage(targetId, "🎉 *Добро пожаловать в Monetka!*\nДоступ открыт, погнали! 🚀",
                    KeyboardFactory.mainMenu());
        }
    }

    private void handleBlock(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long targetId = parseId(text, chatId, bot);
        if (targetId == null) return;
        if (userService.blockUser(targetId)) {
            bot.sendText(chatId, "🚫 Заблокирован: " + targetId);
            bot.sendText(targetId, "😔 Твой доступ заблокирован администратором.");
        }
    }

    private void handleUnblock(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long targetId = parseId(text, chatId, bot);
        if (targetId == null) return;
        if (userService.unblockUser(targetId)) {
            bot.sendText(chatId, "✅ Разблокирован: " + targetId);
            bot.sendMessage(targetId, "🎉 Доступ восстановлен! Рады снова видеть тебя 👋",
                    KeyboardFactory.mainMenu());
        }
    }

    private void notifyAdmins(User user, MonetkaBot bot) {
        String msg = "🆕 *Новая заявка!*\n\n👤 " + user.getDisplayName() + "\n🆔 `" + user.getTelegramId() + "`";
        for (Long adminId : botProperties.getAdminIds()) {
            bot.sendMessage(adminId, msg, KeyboardFactory.pendingUserButtons(user.getTelegramId()));
        }
    }

    // Silent notification for auto-approved users (open mode)
    private void notifyAdminsNewUser(User user, MonetkaBot bot) {
        String msg = "✅ *Новый пользователь*\n\n👤 " + user.getDisplayName() +
                "\n🆔 `" + user.getTelegramId() + "`\n\n_Одобрен автоматически_";
        for (Long adminId : botProperties.getAdminIds()) {
            bot.sendMarkdown(adminId, msg);
        }
    }


    // ================================================================
    // /day — payday cycle status
    // ================================================================

    private void handleDay(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            paydayService.getCycleStatus(user).ifPresentOrElse(s -> {
                String trend;
                int cmp = s.actualDaily.compareTo(s.dailyBudget);
                if (cmp <= 0) trend = "\u2705 \u0418\u0434\u0451\u0448\u044c \u0432 \u043f\u043b\u0430\u043d\u0435 \u2014 \u0432\u0441\u0451 \u043e\u043a!";
                else {
                    java.math.BigDecimal over = s.actualDaily.subtract(s.dailyBudget);
                    trend = "\u26A0\uFE0F \u0422\u0440\u0430\u0442\u0438\u0448\u044c \u043d\u0430 *" +
                            String.format("%,.0f \u0441\u043e\u043c", over) +
                            "* \u0432 \u0434\u0435\u043d\u044c \u0431\u043e\u043b\u044c\u0448\u0435 \u043f\u043b\u0430\u043d\u0430";
                }
                bot.sendMarkdown(chatId,
                        "\uD83D\uDCC5 *\u0426\u0438\u043a\u043b \u0441 " +
                                s.startDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", new java.util.Locale("ru"))) +
                                "* \u2014 \u0434\u0435\u043d\u044c " + s.daysPassed + "\n\n" +
                                "\uD83D\uDCB0 \u0411\u044e\u0434\u0436\u0435\u0442:   *" + String.format("%,.0f \u0441\u043e\u043c", s.totalIncome) + "*\n" +
                                "\uD83D\uDCB8 \u041F\u043E\u0442\u0440\u0430\u0447\u0435\u043D\u043E: *" + String.format("%,.0f \u0441\u043e\u043c", s.spent) + "*\n" +
                                "\uD83D\uDCB5 \u041E\u0441\u0442\u0430\u043B\u043E\u0441\u044C:  *" + String.format("%,.0f \u0441\u043e\u043c", s.remaining) + "*\n\n" +
                                "\uD83D\uDCCA \u041F\u043B\u0430\u043D/\u0434\u0435\u043D\u044C: *" + String.format("%,.0f \u0441\u043e\u043c", s.dailyBudget) + "*\n" +
                                "\uD83D\uDCC8 \u0424\u0430\u043A\u0442/\u0434\u0435\u043D\u044C: *" + String.format("%,.0f \u0441\u043e\u043c", s.actualDaily) + "*\n\n" +
                                trend);
            }, () -> bot.sendMarkdown(chatId,
                    "\uD83D\uDCC5 *\u0414\u0435\u043D\u044C \u0437\u0430\u0440\u043F\u043B\u0430\u0442\u044B*\n\n" +
                            "_\u0426\u0438\u043A\u043B \u0435\u0449\u0451 \u043D\u0435 \u0437\u0430\u043F\u0443\u0449\u0435\u043D._\n\n" +
                            "\u0417\u0430\u043F\u0438\u0448\u0438 \u043B\u044E\u0431\u043E\u0439 \u0434\u043E\u0445\u043E\u0434 \u2014 \u0431\u043E\u0442 \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438 \u043D\u0430\u0447\u043D\u0451\u0442 \u0441\u0447\u0438\u0442\u0430\u0442\u044C \uD83D\uDCA1"));
        });
    }

    // ================================================================
    // Guards
    // ================================================================

    private boolean checkApproved(long chatId, long telegramId, MonetkaBot bot) {
        if (!userService.isActive(telegramId)) {
            bot.sendText(chatId, "⏳ Доступ ещё не подтверждён, подожди немного...");
            return false;
        }
        return true;
    }

    private boolean checkAdmin(long chatId, long telegramId, MonetkaBot bot) {
        if (!botProperties.getAdminIds().contains(telegramId)) {
            bot.sendText(chatId, "⛔ Нет доступа."); return false;
        }
        return true;
    }

    private Long parseId(String text, long chatId, MonetkaBot bot) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2) { bot.sendText(chatId, "Укажи ID. Пример: " + parts[0] + " 123456789"); return null; }
        try { return Long.parseLong(parts[1]); }
        catch (NumberFormatException e) { bot.sendText(chatId, "Некорректный ID: " + parts[1]); return null; }
    }

    private String extractCommand(String text) {
        if (text == null) return "";
        return text.trim().split("\\s+")[0].split("@")[0].toLowerCase();
    }

    private String fmt(BigDecimal amount) { return String.format("%,.0f сом", amount); }

    @SafeVarargs
    private static <T> T pick(T... options) { return options[RND.nextInt(options.length)]; }
}