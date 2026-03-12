package com.monetka.bot.handler;

import com.monetka.admin.AdminHandler;
import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.config.BotProperties;
import com.monetka.model.Debt;
import com.monetka.model.Subscription;
import com.monetka.model.User;
import com.monetka.model.enums.UserState;
import com.monetka.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
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
    private final OnboardingService   onboardingService;
    private final DebtService         debtService;

    public CommandHandler(UserService userService, UserStateService stateService,
                          ReportService reportService, SubscriptionService subscriptionService,
                          BotProperties botProperties, AdminHandler adminHandler,
                          PaydayService paydayService,
                          BotSettingsService botSettingsService,
                          OnboardingService onboardingService,
                          DebtService debtService) {
        this.userService         = userService;
        this.stateService        = stateService;
        this.reportService       = reportService;
        this.subscriptionService = subscriptionService;
        this.botProperties       = botProperties;
        this.adminHandler        = adminHandler;
        this.paydayService       = paydayService;
        this.botSettingsService  = botSettingsService;
        this.onboardingService   = onboardingService;
        this.debtService         = debtService;
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
            case "/debt"          -> handleDebt(chatId, telegramId, bot);
            case "/debts"         -> handleDebt(chatId, telegramId, bot);
            case "/adddebt"       -> handleAddDebt(chatId, telegramId, bot);
            case "/debtstats"     -> handleDebtStats(chatId, telegramId, bot);
            case "/admin"         -> adminHandler.handleCommand(message, bot);
            case "/pending"       -> handlePending(chatId, telegramId, bot);
            case "/blocked"       -> handleBlockedList(chatId, telegramId, bot);
            case "/approve"       -> handleApprove(message.getText(), chatId, telegramId, bot);
            case "/block"         -> handleBlock(message.getText(), chatId, telegramId, bot);
            case "/unblock"       -> handleUnblock(message.getText(), chatId, telegramId, bot);
            default               -> bot.sendText(chatId, "Не знаю такой команды \uD83E\uDD37 Попробуй /help");
        }
    }

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
                    bot.sendText(chatId, "\uD83D\uDC4B Привет, " + user.getDisplayName() + "!\n\nЗаявка отправлена администратору \uD83D\uDCE8\nКак только одобрят — сразу напишу! \u23F3");
                    notifyAdmins(user, bot);
                }
            }
            case ACTIVE -> bot.sendMessage(chatId,
                    pick("\uD83D\uDC4B Привет, " + user.getDisplayName() + "! Готов считать твои деньги \uD83D\uDCAA",
                            "С возвращением, " + user.getDisplayName() + "! \uD83D\uDE80 Что записываем?",
                            "Привет-привет, " + user.getDisplayName() + "! \uD83D\uDCB0 Поехали!"),
                    KeyboardFactory.mainMenu());
            case BLOCKED -> bot.sendText(chatId, "\uD83D\uDE14 Извини, твой доступ заблокирован.");
        }
    }

    private void handleHelp(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        String adminHint = adminHandler.isAdmin(telegramId) ? "\n\n\uD83D\uDEE1 *Ты админ:* /admin" : "";
        bot.sendMarkdown(chatId,
                "*Monetka \u2014 твой личный финансист* \uD83D\uDCB0\n\n" +
                        "*Как записать расход:*\nНажми *\uD83D\uDCB8 Расход* \u2192 введи `название сумма`\nНапример: `шаурма 300`\n\n" +
                        "*Долги:*\n/debt \u2014 мои долги\n/adddebt \u2014 добавить долг\n/debtstats \u2014 статистика\n\n" +
                        "*Другие команды:*\n/balance \u2014 баланс\n/stats \u2014 статистика\n/subscriptions \u2014 подписки\n" +
                        adminHint);
    }

    private void handleCancel(long chatId, long telegramId, MonetkaBot bot) {
        stateService.reset(telegramId);
        bot.sendMessage(chatId,
                pick("Ладно, отменяю \uD83D\uDC4C Что дальше?",
                        "Отменено \u2705 Чем могу помочь?",
                        "Окей, сброс \uD83D\uDD04 Что делаем?"),
                KeyboardFactory.mainMenu());
    }

    private void handleBalance(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            BigDecimal bal = user.getBalance();
            String emoji   = bal.compareTo(BigDecimal.ZERO) >= 0 ? "\uD83D\uDC9A" : "\uD83D\uDD34";
            String comment = bal.compareTo(BigDecimal.ZERO) > 0  ? " \u2014 неплохо! \uD83D\uDC4D"
                    : bal.compareTo(BigDecimal.ZERO) == 0 ? " \u2014 ноль \uD83D\uDE10"
                    : " \u2014 в минусе \u26A0\uFE0F";
            bot.sendMarkdown(chatId, "\uD83D\uDCB3 *Твой баланс:*\n\n" + emoji + " *" + fmt(bal) + "*" + comment);
        });
    }

    private void handleStats(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user ->
                bot.sendMessage(chatId, reportService.buildMonthStats(user), KeyboardFactory.periodPicker()));
    }

    private void handleSubscriptions(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            List<Subscription> subs = subscriptionService.getActiveSubscriptions(user);
            bot.sendMessage(chatId, reportService.buildSubscriptionsList(user),
                    KeyboardFactory.subscriptionActions(subs));
        });
    }

    // ============================================================
    // ДОЛГИ
    // ============================================================

    private void handleDebt(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            List<Debt> active = debtService.getActive(user);
            if (active.isEmpty()) {
                bot.sendMarkdown(chatId, "\uD83D\uDCB3 *Мои долги*\n\nДолгов нет \uD83C\uDF89\n\n_Добавить: /adddebt_");
                return;
            }
            StringBuilder sb = new StringBuilder("\uD83D\uDCB3 *Мои долги*\n\n");
            sb.append("Итого: *").append(fmt(debtService.totalRemaining(user))).append("* осталось\n");
            sb.append("В этом месяце: *").append(fmt(debtService.monthlyObligation(user))).append("*\n\n");
            sb.append("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
            for (Debt d : active) {
                sb.append("\uD83D\uDD38 *").append(d.getName()).append("*\n");
                sb.append("   Осталось: *").append(fmt(d.getRemaining())).append("*");
                int mo = d.monthsLeft();
                if (mo > 0) sb.append(" \u00B7 ~").append(mo).append(" мес");
                sb.append("\n   ").append(d.progressBar());
                sb.append("\n   Триггер: `").append(d.getTriggerWord()).append("`\n\n");
            }
            sb.append("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
            sb.append("_/adddebt \u2014 добавить  |  /debtstats \u2014 статистика_");
            bot.sendMarkdown(chatId, sb.toString());
        });
    }

    private void handleAddDebt(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        stateService.setState(telegramId, UserState.WAITING_DEBT_NAME);
        bot.sendMarkdown(chatId,
                "\uD83D\uDCB3 *Добавляем долг*\n\nКак называется долг?\n\n" +
                        "_Например: Кредит зарплатный, Рассрочка телефон, Долг другу Азизу_");
    }

    private void handleDebtStats(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            List<Debt> active = debtService.getActive(user);
            List<Debt> all    = debtService.getAll(user);
            long closed = all.stream().filter(Debt::isClosed).count();
            StringBuilder sb = new StringBuilder("\uD83D\uDCCA *Статистика по долгам*\n\n");
            sb.append("\uD83D\uDCB8 Выплачено всего: *").append(fmt(debtService.totalEverPaid(user))).append("*\n");
            sb.append("\u2705 Закрыто долгов: *").append(closed).append("*\n");
            sb.append("\u23F3 Активных: *").append(active.size()).append("*\n");
            if (!active.isEmpty()) {
                sb.append("\n\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n*\uD83D\uDCC5 Когда освободятся деньги:*\n");
                active.stream()
                        .sorted((a, b) -> Integer.compare(a.monthsLeft(), b.monthsLeft()))
                        .forEach(d -> {
                            LocalDate c = LocalDate.now().plusMonths(d.monthsLeft());
                            String mon = c.getMonth().getDisplayName(TextStyle.SHORT, new Locale("ru"));
                            sb.append("  \u2022 ").append(d.getName())
                                    .append(" \u2192 *+").append(fmt(d.getMonthlyPayment()))
                                    .append("/мес* (").append(mon).append(" ").append(c.getYear()).append(")\n");
                        });
                sb.append("\n\uD83C\uDF89 После всего: *+")
                        .append(fmt(debtService.monthlyObligation(user))).append("/мес* свободно!\n");
            }
            sb.append("\n_/debt \u2014 список долгов_");
            bot.sendMarkdown(chatId, sb.toString());
        });
    }

    // ============================================================
    // Admin commands
    // ============================================================

    private void handlePending(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        List<User> pending = userService.getPendingUsers();
        if (pending.isEmpty()) { bot.sendText(chatId, "\u2705 Заявок нет."); return; }
        bot.sendMarkdown(chatId, "\u23F3 *Ожидают: " + pending.size() + "*");
        for (User u : pending)
            bot.sendMessage(chatId, "\uD83D\uDC64 *" + u.getDisplayName() + "*\n\uD83C\uDD94 `" + u.getTelegramId() + "`",
                    KeyboardFactory.pendingUserButtons(u.getTelegramId()));
    }

    private void handleBlockedList(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        List<User> blocked = userService.getBlockedUsers();
        if (blocked.isEmpty()) { bot.sendText(chatId, "\u2705 Заблокированных нет."); return; }
        bot.sendMarkdown(chatId, "\uD83D\uDEAB *Заблокированы: " + blocked.size() + "*");
        for (User u : blocked)
            bot.sendMessage(chatId, "\uD83D\uDC64 *" + u.getDisplayName() + "*\n\uD83C\uDD94 `" + u.getTelegramId() + "`",
                    KeyboardFactory.blockedUserButtons(u.getTelegramId()));
    }

    private void handleApprove(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long id = parseId(text, chatId, bot); if (id == null) return;
        if (userService.approveUser(id)) {
            bot.sendText(chatId, "\u2705 Пользователь " + id + " одобрен.");
            bot.sendMessage(id, "\uD83C\uDF89 *Добро пожаловать в Monetka!*\nДоступ открыт, погнали! \uD83D\uDE80",
                    KeyboardFactory.mainMenu());
        }
    }

    private void handleBlock(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long id = parseId(text, chatId, bot); if (id == null) return;
        if (userService.blockUser(id)) {
            bot.sendText(chatId, "\uD83D\uDEAB Заблокирован: " + id);
            bot.sendText(id, "\uD83D\uDE14 Твой доступ заблокирован.");
        }
    }

    private void handleUnblock(String text, long chatId, long telegramId, MonetkaBot bot) {
        if (!checkAdmin(chatId, telegramId, bot)) return;
        Long id = parseId(text, chatId, bot); if (id == null) return;
        if (userService.unblockUser(id)) {
            bot.sendText(chatId, "\u2705 Разблокирован: " + id);
            bot.sendMessage(id, "\uD83C\uDF89 Доступ восстановлен! \uD83D\uDC4B", KeyboardFactory.mainMenu());
        }
    }

    private void notifyAdmins(User user, MonetkaBot bot) {
        String msg = "\uD83C\uDD95 *Новая заявка!*\n\n\uD83D\uDC64 " + user.getDisplayName() + "\n\uD83C\uDD94 `" + user.getTelegramId() + "`";
        for (Long adminId : botProperties.getAdminIds())
            bot.sendMessage(adminId, msg, KeyboardFactory.pendingUserButtons(user.getTelegramId()));
    }

    private void notifyAdminsNewUser(User user, MonetkaBot bot) {
        String msg = "\u2705 *Новый пользователь*\n\n\uD83D\uDC64 " + user.getDisplayName() + "\n\uD83C\uDD94 `" + user.getTelegramId() + "`\n\n_Одобрен автоматически_";
        for (Long adminId : botProperties.getAdminIds()) bot.sendMarkdown(adminId, msg);
    }

    private void handleDay(long chatId, long telegramId, MonetkaBot bot) {
        if (!checkApproved(chatId, telegramId, bot)) return;
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            paydayService.getCycleStatus(user).ifPresentOrElse(s -> {
                String trend = s.actualDaily.compareTo(s.dailyBudget) <= 0
                        ? "\u2705 Идёшь в плане \u2014 всё ок!"
                        : "\u26A0\uFE0F Тратишь на *" + String.format("%,.0f сом", s.actualDaily.subtract(s.dailyBudget)) + "* в день больше плана";
                bot.sendMarkdown(chatId,
                        "\uD83D\uDCC5 *Цикл с " + s.startDate.format(DateTimeFormatter.ofPattern("d MMMM", new Locale("ru"))) + "* \u2014 день " + s.daysPassed + "\n\n" +
                                "\uD83D\uDCB0 Бюджет:   *" + String.format("%,.0f сом", s.totalIncome) + "*\n" +
                                "\uD83D\uDCB8 Потрачено: *" + String.format("%,.0f сом", s.spent) + "*\n" +
                                "\uD83D\uDCB5 Осталось:  *" + String.format("%,.0f сом", s.remaining) + "*\n\n" +
                                "\uD83D\uDCCA План/день: *" + String.format("%,.0f сом", s.dailyBudget) + "*\n" +
                                "\uD83D\uDCC8 Факт/день: *" + String.format("%,.0f сом", s.actualDaily) + "*\n\n" +
                                trend);
            }, () -> bot.sendMarkdown(chatId, "\uD83D\uDCC5 *День зарплаты*\n\n_Цикл ещё не запущен._\n\nЗапиши любой доход \u2014 бот начнёт считать \uD83D\uDCA1"));
        });
    }

    private boolean checkApproved(long chatId, long telegramId, MonetkaBot bot) {
        if (!userService.isActive(telegramId)) { bot.sendText(chatId, "\u23F3 Доступ ещё не подтверждён..."); return false; }
        return true;
    }

    private boolean checkAdmin(long chatId, long telegramId, MonetkaBot bot) {
        if (!botProperties.getAdminIds().contains(telegramId)) { bot.sendText(chatId, "\u26D4 Нет доступа."); return false; }
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