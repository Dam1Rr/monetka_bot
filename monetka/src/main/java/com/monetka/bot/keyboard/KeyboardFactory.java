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
 * All methods are static — no need to inject.
 */
public final class KeyboardFactory {

    private KeyboardFactory() {}

    // ---- Main menu ----

    public static ReplyKeyboardMarkup mainMenu() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        KeyboardRow row1 = new KeyboardRow();
        row1.add("💸 Расход");
        row1.add("💰 Доход");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("📊 Статистика");
        row2.add("💳 Баланс");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("🔄 Подписки");
        row3.add("❓ Помощь");

        keyboard.setKeyboard(List.of(row1, row2, row3));
        return keyboard;
    }

    // ---- Cancel button ----

    public static ReplyKeyboardMarkup cancelOnly() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("❌ Отмена");
        keyboard.setKeyboard(List.of(row));
        return keyboard;
    }

    // ---- Admin pending users ----

    public static InlineKeyboardMarkup adminApproveButtons(Long telegramId) {
        return InlineKeyboardMarkup.builder()
            .keyboardRow(List.of(
                button("✅ Одобрить", "approve:" + telegramId),
                button("❌ Отклонить", "reject:" + telegramId)
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
                button("📅 Сегодня",  "stats:today"),
                button("📆 Месяц",    "stats:month")
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
