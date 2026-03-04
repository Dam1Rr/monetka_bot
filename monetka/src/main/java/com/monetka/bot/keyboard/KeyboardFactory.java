package com.monetka.bot.keyboard;

import com.monetka.model.Subscription;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

public final class KeyboardFactory {

    private KeyboardFactory() {}

    // ---- Главное меню — 2 кнопки ----

    public static ReplyKeyboardMarkup mainMenu() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(false);

        KeyboardRow row = new KeyboardRow();
        row.add("💸 Расход");
        row.add("💰 Доход");

        kb.setKeyboard(List.of(row));
        return kb;
    }

    // ---- Кнопка отмены ----

    public static ReplyKeyboardMarkup cancelMenu() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("❌ Отменить действие");
        kb.setKeyboard(List.of(row));
        return kb;
    }

    // ---- Статистика: сегодня / неделя / месяц ----

    public static InlineKeyboardMarkup statsPeriod() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        button("📅 Сегодня", "stats:today"),
                        button("📆 Неделя",  "stats:week"),
                        button("🗓 Месяц",   "stats:month")
                ))
                .build();
    }

    // ---- Admin: одобрить / заблокировать нового пользователя ----

    public static InlineKeyboardMarkup adminApproveButtons(Long telegramId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        button("✅ Одобрить",      "approve:"    + telegramId),
                        button("🚫 Заблокировать", "block_user:" + telegramId)
                ))
                .build();
    }

    // ---- Admin: действия над конкретным пользователем в списке ----

    public static InlineKeyboardMarkup adminUserActions(Long telegramId, boolean isBlocked) {
        if (isBlocked) {
            return InlineKeyboardMarkup.builder()
                    .keyboardRow(List.of(
                            button("✅ Разблокировать", "unblock_user:" + telegramId)
                    ))
                    .build();
        } else {
            return InlineKeyboardMarkup.builder()
                    .keyboardRow(List.of(
                            button("✅ Одобрить",      "approve:"    + telegramId),
                            button("🚫 Заблокировать", "block_user:" + telegramId)
                    ))
                    .build();
        }
    }

    // ---- Подписки ----

    public static InlineKeyboardMarkup subscriptionActions(List<Subscription> subs) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Subscription sub : subs) {
            rows.add(List.of(
                    button("❌ Удалить: " + sub.getName(), "cancel_sub:" + sub.getId())
            ));
        }
        rows.add(List.of(button("➕ Добавить подписку", "add_sub")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ---- Helper ----

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
}