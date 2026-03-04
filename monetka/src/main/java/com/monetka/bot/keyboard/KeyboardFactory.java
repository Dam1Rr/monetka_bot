package com.monetka.bot.keyboard;

import com.monetka.model.Subscription;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for building Telegram keyboard markups.
 * Main menu: только 2 кнопки — Расход и Доход.
 * Остальные функции через команды.
 */
public final class KeyboardFactory {

    private KeyboardFactory() {}

    // ---- Main menu — только 2 кнопки ----

    public static ReplyKeyboardMarkup mainMenu() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        KeyboardRow row = new KeyboardRow();
        row.add("💸 Расход");
        row.add("💰 Доход");

        keyboard.setKeyboard(List.of(row));
        return keyboard;
    }

    // ---- Отмена (во время ввода) ----

    public static ReplyKeyboardMarkup cancelMenu() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        KeyboardRow row = new KeyboardRow();
        row.add("❌ Отменить действие");

        keyboard.setKeyboard(List.of(row));
        return keyboard;
    }

    // ---- Admin: одобрить / отклонить нового пользователя ----

    public static InlineKeyboardMarkup adminApproveButtons(Long telegramId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        button("✅ Одобрить",   "approve:" + telegramId),
                        button("🚫 Заблокировать", "reject:"  + telegramId)
                ))
                .build();
    }

    // ---- Subscription management ----

    public static InlineKeyboardMarkup subscriptionActions(List<Subscription> subs) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Subscription sub : subs) {
            rows.add(List.of(
                    button("❌ " + sub.getName(), "cancel_sub:" + sub.getId())
            ));
        }
        rows.add(List.of(button("➕ Новая подписка", "add_sub")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ---- Statistics period selector ----

    public static InlineKeyboardMarkup statsPeriod() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        button("📅 Сегодня", "stats:today"),
                        button("📆 Месяц",   "stats:month")
                ))
                .build();
    }

    // ---- Helper ----

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
}