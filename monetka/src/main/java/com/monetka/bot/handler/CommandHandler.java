package com.monetka.bot.handler;

import com.monetka.admin.AdminHandler;
import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.config.BotProperties;
import com.monetka.model.Subscription;
import com.monetka.model.User;
import com.monetka.model.UserReminder;
import com.monetka.model.enums.UserStatus;
import com.monetka.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);

    private final UserService         userService;
    private final UserStateService    stateService;
    private final ReportService       reportService;
    private final SubscriptionService subscriptionService;
    private final BotProperties       botProperties;
    private final AdminHandler        adminHandler;
    private final PaydayService       paydayService;
    private final BotSettingsService  botSettingsService;
    private final OnboardingService   onboardingService;
    private final ReminderService     reminderService;

    public CommandHandler(UserService userService, UserStateService stateService,
                          ReportService reportService, SubscriptionService subscriptionService,
                          BotProperties botProperties, AdminHandler adminHandler,
                          PaydayService paydayService,
                          BotSettingsService botSettingsService,
                          OnboardingService onboardingService,
                          ReminderService reminderService) {
        this.userService         = userService;
        this.stateService        = stateService;
        this.reportService       = reportService;
        this.subscriptionService = subscriptionService;
        this.botProperties       = botProperties;
        this.adminHandler        = adminHandler;
        this.paydayService       = paydayService;
        this.botSettingsService  = botSettingsService;
        this.onboardingService   = onboardingService;
        this.reminderService     = reminderService;
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
            case "/remind"        -> handleRemind(chatId, telegramId, bot);
            case "/reset"         -> handleReset(chatId, telegramId, bot);
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
                    userService.approveUser(telegramId);
                    notifyAdminsNewUser(user, bot);
                    onboardingService.sendWelcome(user, chatId, bot);
                } else {
                    bot.sendText(chatId,
                            "\uD83D\uDC4B Привет, " + user.getDisplayName() + "!\n\n" +
                                    "Заявка отправлена администратору \uD83D\uDCE8\n" +
                                    "Как только одобрят \u2014 сразу напишу! \u23F3");
                    notifyAdmins(user, bot);
                }
            }
            case ACTIVE -> bot.sendMessage(chatId,
                    pick("\uD83D\uDC4B Привет, " + user.getDisplayName() + "! Готов считать твои деньги \uD83D\uDCAA",
                            "\u0421 возвращением, " + user.getDisplayName() + "! \uD83D\uDE80 Что записываем?",
                            "Привет-привет, " + user.getDisplayName() + "! \uD83D\uDCB0 Поехали!"),
                    KeyboardFactory.mainMenu());
            case BLOCKED -> bot.sendText(chatId,
                    "\uD83D\uDE14 Извини, твой доступ заблокирован администратором.");
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
                        "/remind — настройки напоминаний ⏰\n" +
                        "/reset — сбросить все данные 🗑\n\n" +
                        "💡 _Бот учится: чем больше используешь, тем точнее определяет категории!_" +
                        adminHint);
    }

    // ================================================================
    // /reset — сброс всех данных пользователя
    // ================================================================

    private void handleReset(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        bot.sendMarkdown(chatId,
                "🗑 *Сброс данных*\n\n" +
                        "Это удалит *все* твои транзакции и обнулит баланс.\n" +
                        "Лимиты и напоминания сохранятся.\n\n" +
                        "_Действие необратимо. Уверен?_",
                KeyboardFactory.confirmReset());
    }

    // ================================================================
    // /cancel
    // ================================================================

    private void handleCancel(long chatId, long telegramId, MonetkaBot bot) {
        stateService.reset(telegramId);
        bot.sendMessage(chatId,
                pick("Ладно, отменяю \uD83D\uDC4C Что дальше?",
                        "Отменено \u2705 Чем могу помочь?",
                        "Окей, сброс \uD83D\uDD04 Что делаем?"),
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // /balance
    // ================================================================

    private void handleBalance(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            BigDecimal bal = user.getBalance();
            String emoji   = bal.compareTo(BigDecimal.ZERO) >= 0 ? "\uD83D\uDC9A" : "\uD83D\uDD34";
            String comment = bal.compareTo(BigDecimal.ZERO) > 0  ? " \u2014 \u043d\u0435\u043f\u043b\u043e\u0445\u043e! \uD83D\uDC4D"
                    : bal.compareTo(BigDecimal.ZERO) == 0 ? " \u2014 \u043d\u043e\u043b\u044c \uD83D\uDE10"
                    : " \u2014 \u0432 \u043c\u0438\u043d\u0443\u0441\u0435, \u043e\u0441\u0442\u043e\u0440\u043e\u0436\u043d\u043e! \u26A0\uFE0F";
            bot.sendMarkdown(chatId,
                    "\uD83D\uDCB3 *\u0422\u0432\u043e\u0439 \u0431\u0430\u043b\u0430\u043d\u0441:*\n\n" + emoji + " *" + fmt(bal) + "*" + comment);
        });
    }

    // ================================================================
    // /stats
    // ================================================================

    private void handleStats(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user ->
                bot.sendMessage(chatId, reportService.buildMonthStats(user), KeyboardFactory.periodPicker()));
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
    // /day — payday cycle status
    // ================================================================

    private void handleDay(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            paydayService.getCycleStatus(user).ifPresentOrElse(s -> {
                String trend = s.actualDaily.compareTo(s.dailyBudget) <= 0
                        ? "\u2705 \u0418\u0434\u0451\u0448\u044c \u0432 \u043f\u043b\u0430\u043d\u0435 \u2014 \u0432\u0441\u0451 \u043e\u043a!"
                        : "\u26A0\uFE0F \u0422\u0440\u0430\u0442\u0438\u0448\u044c \u043d\u0430 *" +
                        String.format("%,.0f \u0441\u043e\u043c", s.actualDaily.subtract(s.dailyBudget)) +
                        "* \u0432 \u0434\u0435\u043d\u044c \u0431\u043e\u043b\u044c\u0448\u0435 \u043f\u043b\u0430\u043d\u0430";
                bot.sendMarkdown(chatId,
                        "\uD83D\uDCC5 *\u0426\u0438\u043a\u043b \u0441 " +
                                s.startDate.format(DateTimeFormatter.ofPattern("d MMMM", new Locale("ru"))) +
                                "* \u2014 \u0434\u0435\u043d\u044c " + s.daysPassed + "\n\n" +
                                "\uD83D\uDCB0 \u0411\u044e\u0434\u0436\u0435\u0442:   *" + String.format("%,.0f \u0441\u043e\u043c", s.totalIncome) + "*\n" +
                                "\uD83D\uDCB8 \u041f\u043e\u0442\u0440\u0430\u0447\u0435\u043d\u043e: *" + String.format("%,.0f \u0441\u043e\u043c", s.spent) + "*\n" +
                                "\uD83D\uDCB5 \u041e\u0441\u0442\u0430\u043b\u043e\u0441\u044c:  *" + String.format("%,.0f \u0441\u043e\u043c", s.remaining) + "*\n\n" +
                                "\uD83D\uDCCA \u041f\u043b\u0430\u043d/\u0434\u0435\u043d\u044c: *" + String.format("%,.0f \u0441\u043e\u043c", s.dailyBudget) + "*\n" +
                                "\uD83D\uDCC8 \u0424\u0430\u043a\u0442/\u0434\u0435\u043d\u044c: *" + String.format("%,.0f \u0441\u043e\u043c", s.actualDaily) + "*\n\n" +
                                trend);
            }, () -> bot.sendMarkdown(chatId,
                    "\uD83D\uDCC5 *\u0414\u0435\u043d\u044c \u0437\u0430\u0440\u043f\u043b\u0430\u0442\u044b*\n\n" +
                            "_\u0426\u0438\u043a\u043b \u0435\u0449\u0451 \u043d\u0435 \u0437\u0430\u043f\u0443\u0449\u0435\u043d._\n\n" +
                            "\u0417\u0430\u043f\u0438\u0448\u0438 \u043b\u044e\u0431\u043e\u0439 \u0434\u043e\u0445\u043e\u0434 \u2014 \u0431\u043e\u0442 \u0430\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438 \u043d\u0430\u0447\u043d\u0451\u0442 \u0441\u0447\u0438\u0442\u0430\u0442\u044c \uD83D\uDCA1"));
        });
    }

    // ================================================================
    // Admin commands
    // ================================================================

    private void handlePending(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        List<User> pending = userService.getPendingUsers();
        if (pending.isEmpty()) { bot.sendText(chatId, "\u2705 \u0417\u0430\u044f\u0432\u043e\u043a \u043d\u0435\u0442."); return; }
        bot.sendMarkdown(chatId, "\u23F3 *\u041e\u0436\u0438\u0434\u0430\u044e\u0442: " + pending.size() + "*");
        for (User u : pending)
            bot.sendMessage(chatId,
                    "\uD83D\uDC64 *" + u.getDisplayName() + "*\n\uD83C\uDD94 `" + u.getTelegramId() + "`",
                    KeyboardFactory.pendingUserButtons(u.getTelegramId()));
    }

    private void handleBlockedList(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        List<User> blocked = userService.getBlockedUsers();
        if (blocked.isEmpty()) { bot.sendText(chatId, "\u2705 \u0417\u0430\u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u044b\u0445 \u043d\u0435\u0442."); return; }
        bot.sendMarkdown(chatId, "\uD83D\uDEAB *\u0417\u0430\u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u0430\u043d\u044b: " + blocked.size() + "*");
        for (User u : blocked)
            bot.sendMessage(chatId,
                    "\uD83D\uDC64 *" + u.getDisplayName() + "*\n\uD83C\uDD94 `" + u.getTelegramId() + "`",
                    KeyboardFactory.blockedUserButtons(u.getTelegramId()));
    }

    private void handleApprove(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long id = parseId(text, chatId, bot); if (id == null) return;
        if (userService.approveUser(id)) {
            bot.sendText(chatId, "\u2705 \u041f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044c " + id + " \u043e\u0434\u043e\u0431\u0440\u0435\u043d.");
            bot.sendMessage(id,
                    "\uD83C\uDF89 *\u0414\u043e\u0431\u0440\u043e \u043f\u043e\u0436\u0430\u043b\u043e\u0432\u0430\u0442\u044c \u0432 Monetka!*\n\u0414\u043e\u0441\u0442\u0443\u043f \u043e\u0442\u043a\u0440\u044b\u0442, \u043f\u043e\u0433\u043d\u0430\u043b\u0438! \uD83D\uDE80",
                    KeyboardFactory.mainMenu());
        }
    }

    private void handleBlock(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long id = parseId(text, chatId, bot); if (id == null) return;
        if (userService.blockUser(id)) {
            bot.sendText(chatId, "\uD83D\uDEAB \u0417\u0430\u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u0430\u043d: " + id);
            bot.sendText(id, "\uD83D\uDE14 \u0422\u0432\u043e\u0439 \u0434\u043e\u0441\u0442\u0443\u043f \u0437\u0430\u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u0430\u043d \u0430\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0442\u043e\u0440\u043e\u043c.");
        }
    }

    private void handleUnblock(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long id = parseId(text, chatId, bot); if (id == null) return;
        if (userService.unblockUser(id)) {
            bot.sendText(chatId, "\u2705 \u0420\u0430\u0437\u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u0430\u043d: " + id);
            bot.sendMessage(id,
                    "\uD83C\uDF89 \u0414\u043e\u0441\u0442\u0443\u043f \u0432\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d! \u0420\u0430\u0434\u044b \u0441\u043d\u043e\u0432\u0430 \u0432\u0438\u0434\u0435\u0442\u044c \u0442\u0435\u0431\u044f \uD83D\uDC4B",
                    KeyboardFactory.mainMenu());
        }
    }

    private void notifyAdmins(User user, MonetkaBot bot) {
        String msg = "\uD83C\uDD95 *\u041d\u043e\u0432\u0430\u044f \u0437\u0430\u044f\u0432\u043a\u0430!*\n\n\uD83D\uDC64 "
                + user.getDisplayName() + "\n\uD83C\uDD94 `" + user.getTelegramId() + "`";
        for (Long adminId : botProperties.getAdminIds())
            bot.sendMessage(adminId, msg, KeyboardFactory.pendingUserButtons(user.getTelegramId()));
    }

    private void notifyAdminsNewUser(User user, MonetkaBot bot) {
        String msg = "\u2705 *\u041d\u043e\u0432\u044b\u0439 \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044c*\n\n\uD83D\uDC64 "
                + user.getDisplayName() + "\n\uD83C\uDD94 `" + user.getTelegramId() + "`\n\n_\u041e\u0434\u043e\u0431\u0440\u0435\u043d \u0430\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438_";
        for (Long adminId : botProperties.getAdminIds())
            bot.sendMarkdown(adminId, msg);
    }

    // ================================================================
    // Guards & utils
    // ================================================================

    private boolean checkApproved(long chatId, long telegramId, MonetkaBot bot) {
        if (!userService.isActive(telegramId)) {
            bot.sendText(chatId, "\u23F3 \u0414\u043e\u0441\u0442\u0443\u043f \u0435\u0449\u0451 \u043d\u0435 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0451\u043d, \u043f\u043e\u0434\u043e\u0436\u0434\u0438 \u043d\u0435\u043c\u043d\u043e\u0433\u043e...");
            return false;
        }
        return true;
    }

    private boolean checkAdmin(long chatId, long telegramId, MonetkaBot bot) {
        if (!botProperties.getAdminIds().contains(telegramId)) {
            bot.sendText(chatId, "\u26D4 \u041d\u0435\u0442 \u0434\u043e\u0441\u0442\u0443\u043f\u0430."); return false;
        }
        return true;
    }

    private Long parseId(String text, long chatId, MonetkaBot bot) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2) {
            bot.sendText(chatId, "\u0423\u043a\u0430\u0436\u0438 ID. \u041f\u0440\u0438\u043c\u0435\u0440: " + parts[0] + " 123456789");
            return null;
        }
        try { return Long.parseLong(parts[1]); }
        catch (NumberFormatException e) {
            bot.sendText(chatId, "\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 ID: " + parts[1]);
            return null;
        }
    }

    private String extractCommand(String text) {
        if (text == null) return "";
        return text.trim().split("\\s+")[0].split("@")[0].toLowerCase();
    }

    private String fmt(BigDecimal amount) { return String.format("%,.0f сом", amount); }

    // ================================================================
    // /remind — настройки напоминаний
    // ================================================================

    private void handleRemind(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            com.monetka.model.UserReminder r = reminderService.getOrCreate(user);
            bot.sendMarkdown(chatId,
                    "⏰ *Напоминания*\n\n" +
                            reminderService.statusText(r) + "\n" +
                            "_Напоминания помогают не забывать записывать траты каждый день._",
                    KeyboardFactory.remindMenu(r));
        });
    }

    @SafeVarargs
    private static <T> T pick(T... options) {
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }
}