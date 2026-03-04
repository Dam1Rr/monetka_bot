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
    private final StatisticsService   statisticsService;
    private final BotProperties       botProperties;

    public void handle(CallbackQuery callback, MonetkaBot bot) {
        long   chatId     = callback.getMessage().getChatId();
        long   telegramId = callback.getFrom().getId();
        String data       = callback.getData();

        answerCallback(callback.getId(), bot);

        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) return;
        User user = userOpt.get();

        // ---- Admin: approve / reject ----
        if (data.startsWith("approve:")) {
            handleApprove(data, chatId, telegramId, bot);

        } else if (data.startsWith("reject:")) {
            handleReject(data, chatId, telegramId, bot);

        // ---- Subscription: cancel ----
        } else if (data.startsWith("cancel_sub:")) {
            handleCancelSub(user, data, chatId, bot);

        } else if (data.equals("add_sub")) {
            stateService.setState(telegramId, UserState.WAITING_SUB_NAME);
            bot.sendMessage(chatId, "Введи название подписки:", KeyboardFactory.cancelOnly());

        // ---- Statistics period ----
        } else if (data.startsWith("stats:")) {
            handleStats(user, data, chatId, bot);

        } else {
            log.warn("Unknown callback: {}", data);
        }
    }

    // ---- Admin approve ----

    private void handleApprove(String data, long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) return;

        long targetId = Long.parseLong(data.split(":")[1]);
        if (userService.approveUser(targetId)) {
            bot.sendText(chatId, "✅ Пользователь " + targetId + " одобрен.");
            bot.sendMessage(targetId,
                "✅ Ваш доступ подтверждён! Добро пожаловать в Monetka 🎉",
                KeyboardFactory.mainMenu());
        }
    }

    private void handleReject(String data, long chatId, long telegramId, MonetkaBot bot) {
        if (!isAdmin(telegramId)) return;

        long targetId = Long.parseLong(data.split(":")[1]);
        if (userService.blockUser(targetId)) {
            bot.sendText(chatId, "🚫 Пользователь " + targetId + " отклонён.");
            bot.sendText(targetId, "❌ Ваша заявка на доступ отклонена.");
        }
    }

    // ---- Cancel subscription ----

    private void handleCancelSub(User user, String data, long chatId, MonetkaBot bot) {
        long subId = Long.parseLong(data.split(":")[1]);
        boolean cancelled = subscriptionService.cancel(user, subId);

        if (cancelled) {
            var subs = subscriptionService.getActiveSubscriptions(user);
            bot.sendMessage(chatId,
                "✅ Подписка отменена.\n\n" + reportService.buildSubscriptionsList(user),
                KeyboardFactory.subscriptionActions(subs));
        } else {
            bot.sendText(chatId, "Подписка не найдена.");
        }
    }

    // ---- Statistics ----

    private void handleStats(User user, String data, long chatId, MonetkaBot bot) {
        String period = data.split(":")[1];
        String report = switch (period) {
            case "today" -> buildTodayStats(user);
            case "month" -> reportService.buildMonthStats(user);
            default      -> "Неизвестный период";
        };
        bot.sendMarkdown(chatId, report);
    }

    private String buildTodayStats(User user) {
        var from = java.time.LocalDate.now().atStartOfDay();
        var to   = from.plusDays(1);
        var byCategory = statisticsService.getExpensesByCategory(user, from, to);

        StringBuilder sb = new StringBuilder("📊 *Статистика за сегодня*\n\n");
        if (byCategory.isEmpty()) {
            sb.append("Расходов сегодня нет 🌙");
        } else {
            byCategory.forEach((cat, amt) ->
                sb.append(cat).append(" — ")
                  .append(String.format("%,.0f ₸", amt)).append("\n"));
        }
        return sb.toString();
    }

    // ---- Helpers ----

    private boolean isAdmin(long telegramId) {
        return botProperties.getAdminIds().contains(telegramId);
    }

    private void answerCallback(String callbackId, MonetkaBot bot) {
        try {
            bot.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(callbackId)
                .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback: {}", e.getMessage());
        }
    }
}
