package com.monetka.bot.handler;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackHandler {

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

    public void handle(CallbackQuery callback, MonetkaBot bot) {
        long   chatId     = callback.getMessage().getChatId();
        long   telegramId = callback.getFrom().getId();
        String data       = callback.getData();

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
        else if (data.startsWith("cat:"))          handleCategoryChoice(user, data, chatId, telegramId, bot);
        else if (data.startsWith("subcat:"))       handleSubcategoryChoice(user, data, chatId, telegramId, bot);
        else log.warn("Unknown callback: {}", data);
    }

    // ================================================================
    // Выбор категории вручную
    // ================================================================

    private void handleCategoryChoice(User user, String data, long chatId, long telegramId, MonetkaBot bot) {
        long catId = Long.parseLong(data.split(":")[1]);

        Optional<Category> catOpt = categoryRepository.findById(catId);
        if (catOpt.isEmpty()) { bot.sendText(chatId, "Категория не найдена."); return; }

        Category category = catOpt.get();

        // Сохраняем выбранную категорию
        stateService.putData(telegramId, "chosen_category_id", String.valueOf(catId));
        stateService.setState(telegramId, UserState.WAITING_SUBCATEGORY_CHOICE);

        if (category.getSubcategories().isEmpty()) {
            // Нет подкатегорий — сохраняем сразу
            saveExpenseWithCategory(user, category, null, chatId, telegramId, bot);
        } else {
            bot.sendMessage(chatId,
                    "Выбрана: *" + category.getDisplayName() + "*\n\nТеперь выбери подкатегорию:",
                    KeyboardFactory.subcategoryChoice(category.getSubcategories(), catId));
        }
    }

    private void handleSubcategoryChoice(User user, String data, long chatId,
                                         long telegramId, MonetkaBot bot) {
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
            bot.sendMessage(chatId, "Что-то пошло не так. Попробуй снова.", KeyboardFactory.mainMenu());
            return;
        }

        BigDecimal amount = new BigDecimal(amtStr);
        Transaction tx = transactionService.addExpense(user, amount, desc);

        // Обучаем на основе выбора пользователя
        String keyword = detectionService.normalize(desc);
        if (!keyword.isBlank()) {
            detectionService.learnKeyword(keyword.split("\\s+")[0], category, subcategory,
                    user.getTelegramId());
        }

        // Обновляем категорию транзакции
        tx.setCategory(category);
        tx.setSubcategory(subcategory);
        transactionRepository.save(tx);

        stateService.reset(telegramId);

        String catDisplay = subcategory != null
                ? category.getDisplayName() + " → " + subcategory.getDisplayName()
                : category.getDisplayName();

        bot.sendMessage(chatId,
                "✅ *Расход сохранён!*\n\n" +
                        "📝 " + desc + "\n" +
                        "💸 −" + fmt(amount) + "\n" +
                        "🏷 " + catDisplay + "\n" +
                        "💳 Баланс: " + fmt(user.getBalance()) + "\n\n" +
                        "💡 _Запомнил! В следующий раз определю автоматически._",
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // Admin callbacks
    // ================================================================

    private void handleApprove(String data, long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) return;
        long targetId = parseId(data);
        if (userService.approveUser(targetId)) {
            bot.sendText(chatId, "✅ Пользователь " + targetId + " одобрен.");
            bot.sendMessage(targetId,
                    "✅ *Добро пожаловать в Monetka!*\n\nТвой доступ подтверждён 🎉",
                    KeyboardFactory.mainMenu());
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
    // Subscription callbacks
    // ================================================================

    private void handleCancelSub(User user, String data, long chatId, MonetkaBot bot) {
        long subId = parseId(data);
        if (subscriptionService.cancel(user, subId)) {
            var subs = subscriptionService.getActiveSubscriptions(user);
            bot.sendMessage(chatId,
                    "✅ Подписка удалена.\n\n" + reportService.buildSubscriptionsList(user),
                    KeyboardFactory.subscriptionActions(subs));
        } else {
            bot.sendText(chatId, "Подписка не найдена.");
        }
    }

    private void handleAddSub(long telegramId, long chatId, MonetkaBot bot) {
        stateService.setState(telegramId, UserState.WAITING_SUB_NAME);
        bot.sendMessage(chatId, "Введи название подписки:", KeyboardFactory.cancelMenu());
    }

    // ================================================================
    // Stats
    // ================================================================

    private void handleStats(User user, String data, long chatId, MonetkaBot bot) {
        String period = data.split(":")[1];
        String report = switch (period) {
            case "today" -> reportService.buildTodayStats(user);
            case "week"  -> reportService.buildWeekStats(user);
            case "month" -> reportService.buildMonthStats(user);
            default      -> "Неизвестный период";
        };
        bot.sendMessage(chatId, report, KeyboardFactory.statsPeriod());
    }

    // ================================================================
    // Helpers
    // ================================================================

    private boolean isAdmin(long telegramId) {
        return botProperties.getAdminIds().contains(telegramId);
    }

    private long parseId(String data) {
        return Long.parseLong(data.split(":")[1]);
    }

    private String fmt(BigDecimal amount) {
        return String.format("%,.0f сом", amount);
    }

    private void answerCallback(String callbackId, MonetkaBot bot) {
        try {
            bot.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback: {}", e.getMessage());
        }
    }
}