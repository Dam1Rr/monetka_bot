package com.monetka.bot.handler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.config.BotProperties;
import com.monetka.model.User;
import com.monetka.model.enums.UserState;
import com.monetka.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackHandler {

    private final UserService         userService;
    private final UserStateService    stateService;
    private final SubscriptionService subscriptionService;
    private final ReportService       reportService;
    private final BotProperties       botProperties;

    public void handle(CallbackQuery callback, MonetkaBot bot) {
        long   chatId     = callback.getMessage().getChatId();
        long   telegramId = callback.getFrom().getId();
        String data       = callback.getData();

        answerCallback(callback.getId(), bot);

        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) return;
        User user = userOpt.get();

        if      (data.startsWith("approve:"))     handleApprove(data, chatId, telegramId, bot);
        else if (data.startsWith("block_user:"))  handleBlockUser(data, chatId, telegramId, bot);
        else if (data.startsWith("unblock_user:"))handleUnblockUser(data, chatId, telegramId, bot);
        else if (data.startsWith("cancel_sub:"))  handleCancelSub(user, data, chatId, bot);
        else if (data.equals("add_sub"))          handleAddSub(telegramId, chatId, bot);
        else if (data.startsWith("stats:"))       handleStats(user, data, chatId, bot);
        else log.warn("Unknown callback: {}", data);
    }

    // ================================================================
    // Admin callbacks
    // ================================================================

    private void handleApprove(String data, long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) return;
        long targetId = parseTargetId(data);

        if (userService.approveUser(targetId)) {
            bot.sendText(chatId, "✅ Пользователь " + targetId + " одобрен.");
            bot.sendMessage(targetId,
                    "✅ *Добро пожаловать в Monetka!*\n\nТвой доступ подтверждён. Начнём 💪",
                    KeyboardFactory.mainMenu());
        }
    }

    private void handleBlockUser(String data, long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) return;
        long targetId = parseTargetId(data);

        if (userService.blockUser(targetId)) {
            bot.sendText(chatId, "🚫 Пользователь " + targetId + " заблокирован.");
            bot.sendText(targetId, "Ваш доступ к Monetka был заблокирован администратором.");
        }
    }

    private void handleUnblockUser(String data, long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) return;
        long targetId = parseTargetId(data);

        if (userService.unblockUser(targetId)) {
            bot.sendText(chatId, "✅ Пользователь " + targetId + " разблокирован.");
            bot.sendMessage(targetId,
                    "✅ Твой доступ к Monetka восстановлен!",
                    KeyboardFactory.mainMenu());
        }
    }

    // ================================================================
    // Subscription callbacks
    // ================================================================

    private void handleCancelSub(User user, String data, long chatId, MonetkaBot bot) {
        long subId = parseTargetId(data);
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
        bot.sendMessage(chatId, "Введи название подписки:\n_(например: Netflix, Spotify)_",
                KeyboardFactory.cancelMenu());
    }

    // ================================================================
    // Stats callbacks
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

    private long parseTargetId(String data) {
        return Long.parseLong(data.split(":")[1]);
    }

    private void answerCallback(String callbackId, MonetkaBot bot) {
        try {
            bot.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback: {}", e.getMessage());
        }
    }
}