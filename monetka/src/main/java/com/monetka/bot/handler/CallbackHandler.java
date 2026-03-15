package com.monetka.bot.handler;

import com.monetka.admin.AdminHandler;
import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.config.BotProperties;
import com.monetka.model.Category;
import com.monetka.model.Subcategory;
import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.enums.UserState;
import com.monetka.repository.CategoryRepository;
import com.monetka.repository.SubcategoryRepository;
import com.monetka.repository.TransactionRepository;
import com.monetka.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class CallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(CallbackHandler.class);

    private final UserService              userService;
    private final UserStateService         stateService;
    private final TransactionService       transactionService;
    private final SubscriptionService      subscriptionService;
    private final ReportService            reportService;
    private final CategoryDetectionService detectionService;
    private final CategoryRepository       categoryRepository;
    private final SubcategoryRepository    subcategoryRepository;
    private final TransactionRepository    transactionRepository;
    private final BotProperties            botProperties;
    private final AdminHandler             adminHandler;
    private final OverviewHandler          overviewHandler;
    private final OnboardingService        onboardingService;
    private final ReminderService          reminderService;

    public CallbackHandler(UserService userService, UserStateService stateService,
                           TransactionService transactionService, SubscriptionService subscriptionService,
                           ReportService reportService, CategoryDetectionService detectionService,
                           CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository,
                           TransactionRepository transactionRepository, BotProperties botProperties,
                           AdminHandler adminHandler,
                           OverviewHandler overviewHandler,
                           OnboardingService onboardingService,
                           ReminderService reminderService) {
        this.userService          = userService;
        this.stateService         = stateService;
        this.transactionService   = transactionService;
        this.subscriptionService  = subscriptionService;
        this.reportService        = reportService;
        this.detectionService     = detectionService;
        this.categoryRepository   = categoryRepository;
        this.subcategoryRepository= subcategoryRepository;
        this.transactionRepository= transactionRepository;
        this.botProperties        = botProperties;
        this.adminHandler         = adminHandler;
        this.overviewHandler      = overviewHandler;
        this.onboardingService    = onboardingService;
        this.reminderService      = reminderService;
    }

    public void handle(CallbackQuery callback, MonetkaBot bot) {
        long   chatId     = callback.getMessage().getChatId();
        long   telegramId = callback.getFrom().getId();
        String data       = callback.getData();

        // ── Admin panel callbacks ─────────────────────────────────
        if (data.startsWith("adm:")) {
            adminHandler.handleCallback(callback, bot);
            return;
        }

        // ── Regular user callbacks ────────────────────────────────
        answerCallback(callback.getId(), bot);

        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) return;
        User user = userOpt.get();

        if      (data.startsWith("approve:"))      handleApprove(data, chatId, telegramId, bot);
        else if (data.startsWith("block_user:"))   handleBlockUser(data, chatId, telegramId, bot);
        else if (data.startsWith("unblock_user:")) handleUnblockUser(data, chatId, telegramId, bot);
        else if (data.startsWith("cancel_sub:"))   handleCancelSub(user, data, chatId, bot);
        else if (data.equals("add_sub"))           handleAddSub(telegramId, chatId, bot);
        else if (data.startsWith("stats:"))        handleStats(user, data, chatId, bot);
        else if (data.startsWith("suggest:"))      handleSuggest(user, data, chatId, telegramId, bot);
        else if (data.startsWith("cat:"))          handleCategoryChoice(user, data, chatId, telegramId, bot);
        else if (data.startsWith("subcat:"))       handleSubcategoryChoice(user, data, chatId, telegramId, bot);
        else if (data.startsWith("overview:"))     overviewHandler.handle(data.substring(9), user, chatId, callback.getMessage().getMessageId(), bot);
        else if (data.startsWith("onb:"))          handleOnboarding(data, chatId, user, bot);
        else if (data.startsWith("remind:"))       handleRemind(data, user, chatId, bot);
        else log.warn("Unknown callback: {}", data);
    }

    // ================================================================
    // "Возможно ты имел в виду?" suggestion handling
    // ================================================================

    private void handleSuggest(User user, String data, long chatId, long telegramId, MonetkaBot bot) {
        // suggest:yes:catId:subId  OR  suggest:no
        if (data.equals("suggest:no")) {
            stateService.setState(telegramId, UserState.WAITING_CATEGORY_CHOICE);
            String desc = stateService.getData(telegramId, "expense_desc");
            bot.sendMarkdown(chatId,
                    "Хорошо, выбери категорию для *" + (desc != null ? desc : "расхода") + "*:",
                    KeyboardFactory.categoryChoice(categoryRepository.findAll()));
            return;
        }
        // suggest:yes:catId:subId
        String[] parts = data.split(":");
        if (parts.length < 4) return;
        long catId = Long.parseLong(parts[2]);
        long subId = Long.parseLong(parts[3]);
        Category category = categoryRepository.findById(catId).orElse(null);
        if (category == null) return;
        Subcategory subcategory = subId > 0 ? subcategoryRepository.findById(subId).orElse(null) : null;
        saveExpenseWithCategory(user, category, subcategory, chatId, telegramId, bot);
    }

    // ================================================================
    // Manual category selection
    // ================================================================

    private void handleCategoryChoice(User user, String data, long chatId, long telegramId, MonetkaBot bot) {
        long catId = Long.parseLong(data.split(":")[1]);
        Optional<Category> catOpt = categoryRepository.findById(catId);
        if (catOpt.isEmpty()) { bot.sendText(chatId, "Категория не найдена."); return; }
        Category category = catOpt.get();
        stateService.putData(telegramId, "chosen_category_id", String.valueOf(catId));
        stateService.setState(telegramId, UserState.WAITING_SUBCATEGORY_CHOICE);
        if (category.getSubcategories().isEmpty()) {
            saveExpenseWithCategory(user, category, null, chatId, telegramId, bot);
        } else {
            bot.sendMarkdown(chatId,
                    "Выбрана: *" + category.getDisplayName() + "*\n\nТеперь выбери подкатегорию:",
                    KeyboardFactory.subcategoryChoice(category.getSubcategories(), catId));
        }
    }

    private void handleSubcategoryChoice(User user, String data, long chatId, long telegramId, MonetkaBot bot) {
        String catIdStr = stateService.getData(telegramId, "chosen_category_id");
        if (catIdStr == null) { stateService.reset(telegramId); return; }
        Category category = categoryRepository.findById(Long.parseLong(catIdStr)).orElse(null);
        if (category == null) { stateService.reset(telegramId); return; }
        String subPart = data.split(":")[1];
        Subcategory subcategory = null;
        if (!subPart.equals("skip")) {
            subcategory = subcategoryRepository.findById(Long.parseLong(subPart)).orElse(null);
        }
        saveExpenseWithCategory(user, category, subcategory, chatId, telegramId, bot);
    }

    private void saveExpenseWithCategory(User user, Category category, Subcategory subcategory,
                                         long chatId, long telegramId, MonetkaBot bot) {
        String desc   = stateService.getData(telegramId, "expense_desc");
        String amtStr = stateService.getData(telegramId, "expense_amount");
        if (desc == null || amtStr == null) {
            stateService.reset(telegramId);
            bot.sendMessage(chatId, "Что-то пошло не так. Попробуй снова.", KeyboardFactory.mainMenu()); return;
        }
        BigDecimal amount = new BigDecimal(amtStr);
        // Use overload that accepts explicit category — skips unnecessary auto-detection
        Transaction tx = transactionService.addExpense(user, amount, desc, category, subcategory);
        // Учим все значимые токены из описания (не только первый)
        String normalized = detectionService.normalize(desc);
        if (!normalized.isBlank()) {
            for (String token : normalized.split("\\s+")) {
                if (token.length() >= 3) {
                    detectionService.learnKeyword(token, category, subcategory, user.getTelegramId());
                }
            }
            // Также учим целую фразу если она короткая (до 3 слов)
            String[] parts2 = normalized.split("\\s+");
            if (parts2.length > 1 && parts2.length <= 3) {
                detectionService.learnKeyword(normalized, category, subcategory, user.getTelegramId());
            }
        }
        stateService.reset(telegramId);
        String catDisplay = subcategory != null
                ? category.getDisplayName() + " → " + subcategory.getDisplayName()
                : category.getDisplayName();
        bot.sendMessage(chatId,
                "✅ *Расход сохранён!*\n\n📝 " + com.monetka.bot.MonetkaBot.esc(desc) + "\n💸 −" + fmt(amount) +
                        "\n🏷 " + catDisplay + "\n💳 Баланс: " + fmt(user.getBalance()) +
                        "\n\n💡 _Запомнил! В следующий раз определю автоматически._",
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // Onboarding callbacks
    // ================================================================

    private void handleOnboarding(String data, long chatId, User user, MonetkaBot bot) {
        switch (data) {
            case "onb:step2"  -> onboardingService.sendHowToRecord(chatId, bot);
            case "onb:step3"  -> onboardingService.sendResetOffer(chatId, bot);
            case "onb:reset"  -> {
                // Delete all user transactions and start fresh
                transactionRepository.deleteAllByUser(user);
                onboardingService.sendAskInitialBalance(chatId, bot);
                stateService.setState(user.getTelegramId(), com.monetka.model.enums.UserState.WAITING_INITIAL_BALANCE);
            }
            case "onb:balance" -> {
                // Keep data, just ask balance
                onboardingService.sendAskInitialBalance(chatId, bot);
                stateService.setState(user.getTelegramId(), com.monetka.model.enums.UserState.WAITING_INITIAL_BALANCE);
            }
            case "onb:balance_skip" -> {
                stateService.reset(user.getTelegramId());
                onboardingService.sendFinish(chatId, bot);
            }
            case "onb:finish" -> onboardingService.sendFinish(chatId, bot);
            case "onb:skip"   -> onboardingService.sendSkip(chatId, bot);
            default           -> onboardingService.sendFinish(chatId, bot);
        }
    }

    // ================================================================
    // Legacy admin callbacks (from old inline buttons in CommandHandler)
    // ================================================================

    private void handleApprove(String data, long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) return;
        long targetId = parseId(data);
        if (userService.approveUser(targetId)) {
            bot.sendText(chatId, "✅ Пользователь " + targetId + " одобрен.");
            userService.findByTelegramId(targetId).ifPresent(u ->
                    onboardingService.sendWelcome(u, targetId, bot));
        }
    }

    private void handleBlockUser(String data, long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) return;
        long targetId = parseId(data);
        if (userService.blockUser(targetId)) {
            bot.sendText(chatId, "🚫 Пользователь " + targetId + " заблокирован.");
            bot.sendText(targetId, "Ваш доступ к Monetka был заблокирован администратором.");
        }
    }

    private void handleUnblockUser(String data, long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) return;
        long targetId = parseId(data);
        if (userService.unblockUser(targetId)) {
            bot.sendText(chatId, "✅ Пользователь " + targetId + " разблокирован.");
            bot.sendMessage(targetId, "✅ Твой доступ к Monetka восстановлен!", KeyboardFactory.mainMenu());
        }
    }

    // ================================================================
    // Subscriptions
    // ================================================================

    private void handleCancelSub(User user, String data, long chatId, MonetkaBot bot) {
        long subId = parseId(data);
        if (subscriptionService.cancel(user, subId)) {
            bot.sendMessage(chatId,
                    "✅ Подписка удалена.\n\n" + reportService.buildSubscriptionsList(user),
                    KeyboardFactory.subscriptionActions(subscriptionService.getActiveSubscriptions(user)));
        } else {
            bot.sendText(chatId, "Подписка не найдена.");
        }
    }

    private void handleAddSub(long telegramId, long chatId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_SUB_NAME);
        bot.sendMessage(chatId, "Введи название подписки:", KeyboardFactory.cancelMenu());
    }

    // ================================================================
    // Statistics
    // ================================================================

    private void handleStats(User user, String data, long chatId, MonetkaBot bot) {
        // data examples: stats:today  stats:week  stats:month  stats:cal
        //                stats:cal:prev:2026:3   stats:cal:next:2026:3
        //                stats:cal:day:2026:3:8   stats:cal:confirm:2026:3:5:8
        String[] parts = data.split(":");
        String sub = parts[1]; // today / week / month / cal

        if (sub.equals("cal")) {
            handleStatsCalendar(user, parts, chatId, bot);
            return;
        }

        String report = switch (sub) {
            case "today" -> reportService.buildTodayStats(user);
            case "week"  -> reportService.buildWeekStats(user);
            case "month" -> reportService.buildMonthStats(user);
            default      -> "Неизвестный период";
        };
        bot.sendMarkdown(chatId, report, KeyboardFactory.periodPicker());
    }

    private void handleStatsCalendar(User user, String[] parts, long chatId, MonetkaBot bot) {
        // parts[0]=stats, parts[1]=cal, parts[2]=sub-action...
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bishkek"));
        int year  = today.getYear();
        int month = today.getMonthValue();

        if (parts.length < 3) {
            // First open — show current month, no selection
            bot.sendMarkdown(chatId,
                    "\uD83D\uDDD3 *Выбери период*\n\n_Тап на день — начало, второй тап — конец:_",
                    KeyboardFactory.calendarMonth(year, month, null, null));
            return;
        }

        String action = parts[2]; // prev / next / day / confirm

        if (action.equals("prev") || action.equals("next")) {
            year  = Integer.parseInt(parts[3]);
            month = Integer.parseInt(parts[4]);
            if (action.equals("next")) { month++; if (month > 12) { month = 1; year++; } }
            else                       { month--; if (month < 1)  { month = 12; year--; } }
            bot.sendMarkdown(chatId,
                    "\uD83D\uDDD3 *Выбери период*\n\n_Тап на день — начало, второй тап — конец:_",
                    KeyboardFactory.calendarMonth(year, month, null, null));
            return;
        }

        if (action.equals("day")) {
            year  = Integer.parseInt(parts[3]);
            month = Integer.parseInt(parts[4]);
            int day = Integer.parseInt(parts[5]);
            String stored = stateService.getData(user.getTelegramId(), "cal_start");
            String storedEnd = stateService.getData(user.getTelegramId(), "cal_end");
            if (stored == null || stored.isBlank() || (storedEnd != null && !storedEnd.isBlank())) {
                stateService.putData(user.getTelegramId(), "cal_start", year + ":" + month + ":" + day);
                stateService.putData(user.getTelegramId(), "cal_end", "");
                bot.sendMarkdown(chatId,
                        "\uD83D\uDDD3 *Выбери период*\n\n_Теперь выбери конец периода:_",
                        KeyboardFactory.calendarMonth(year, month, day, null));
            } else {
                // Parse stored start — only use same month
                String[] sp = stored.split(":");
                int sYear = Integer.parseInt(sp[0]);
                int sMonth = Integer.parseInt(sp[1]);
                int startDay = Integer.parseInt(sp[2]);
                int endDay = day;
                // If different month selected as end — force same month, swap if needed
                if (sYear != year || sMonth != month) {
                    // Reset to fresh start selection in new month
                    stateService.putData(user.getTelegramId(), "cal_start", year + ":" + month + ":" + day);
                    stateService.putData(user.getTelegramId(), "cal_end", "");
                    bot.sendMarkdown(chatId,
                            "\uD83D\uDDD3 *Выбери период*\n\n_Теперь выбери конец периода:_",
                            KeyboardFactory.calendarMonth(year, month, day, null));
                    return;
                }
                if (endDay < startDay) { int t = startDay; startDay = endDay; endDay = t; }
                stateService.putData(user.getTelegramId(), "cal_end", year + ":" + month + ":" + endDay);
                bot.sendMarkdown(chatId,
                        "\uD83D\uDDD3 *Выбери период*\n\n_Нажми ✅ чтобы подтвердить:_",
                        KeyboardFactory.calendarMonth(year, month, startDay, endDay));
            }
            return;
        }

        if (action.equals("confirm")) {
            if (parts.length < 7) return;
            year  = Integer.parseInt(parts[3]);
            month = Integer.parseInt(parts[4]);
            int start = Integer.parseInt(parts[5]);
            int end   = Integer.parseInt(parts[6]);
            stateService.putData(user.getTelegramId(), "cal_start", "");
            stateService.putData(user.getTelegramId(), "cal_end", "");

            java.time.LocalDateTime from = java.time.LocalDate.of(year, month, start).atStartOfDay();
            java.time.LocalDateTime to   = java.time.LocalDate.of(year, month, end).plusDays(1).atStartOfDay();
            java.time.Month m = java.time.Month.of(month);
            String label = start + "–" + end + " " +
                    m.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, new java.util.Locale("ru"));
            String report = reportService.buildRangeStats(user, from, to, label);
            bot.sendMarkdown(chatId, report, KeyboardFactory.periodPicker());
        }
    }

    // ================================================================
    // Remind callbacks
    // ================================================================

    private void handleRemind(String data, User user, long chatId, MonetkaBot bot) {
        String action = data.substring("remind:".length());
        com.monetka.model.UserReminder r = switch (action) {
            case "on"             -> reminderService.setEnabled(user, true);
            case "off"            -> reminderService.setEnabled(user, false);
            case "toggle_morning" -> reminderService.toggleMorning(user);
            case "toggle_evening" -> reminderService.toggleEvening(user);
            default               -> reminderService.getOrCreate(user);
        };
        bot.sendMarkdown(chatId,
                "⏰ *Напоминания*\n\n" +
                        reminderService.statusText(r) + "\n" +
                        "_Напоминания помогают не забывать записывать траты каждый день._",
                KeyboardFactory.remindMenu(r));
    }

    // ================================================================
    // Helpers
    // ================================================================

    private boolean isAdmin(long telegramId) {
        return botProperties.getAdminIds().contains(telegramId);
    }

    private long parseId(String data) { return Long.parseLong(data.split(":")[1]); }

    private String fmt(BigDecimal amount) { return String.format("%,.0f сом", amount); }

    private void answerCallback(String callbackId, MonetkaBot bot) {
        try {
            bot.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback: {}", e.getMessage());
        }
    }
}