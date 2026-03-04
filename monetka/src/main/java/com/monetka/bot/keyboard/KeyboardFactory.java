package com.monetka.bot.keyboard;

import com.monetka.model.Category;
import com.monetka.model.Subcategory;
import com.monetka.model.Subscription;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

public final class KeyboardFactory {

    private KeyboardFactory() {}

    // ================================================================
    // Reply keyboards
    // ================================================================

    public static ReplyKeyboardMarkup mainMenu() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow row = new KeyboardRow();
        row.add("💸 Расход");
        row.add("💰 Доход");
        kb.setKeyboard(List.of(row));
        return kb;
    }

    public static ReplyKeyboardMarkup cancelMenu() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow row = new KeyboardRow();
        row.add("❌ Отменить действие");
        kb.setKeyboard(List.of(row));
        return kb;
    }

    public static ReplyKeyboardMarkup cancelWithSkip(String skipLabel) {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow row = new KeyboardRow();
        row.add("⏭ " + skipLabel);
        row.add("❌ Отменить действие");
        kb.setKeyboard(List.of(row));
        return kb;
    }

    // ================================================================
    // Категории — выбор вручную
    // ================================================================

    /** Кнопки категорий когда бот не распознал */
    public static InlineKeyboardMarkup categoryChoice(List<Category> categories) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        for (Category cat : categories) {
            row.add(button(cat.getDisplayName(), "cat:" + cat.getId()));
            if (row.size() == 2) {
                rows.add(new ArrayList<>(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) rows.add(row);

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Кнопки подкатегорий */
    public static InlineKeyboardMarkup subcategoryChoice(List<Subcategory> subs, long categoryId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        for (Subcategory sub : subs) {
            row.add(button(sub.getDisplayName(), "subcat:" + sub.getId()));
            if (row.size() == 2) {
                rows.add(new ArrayList<>(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) rows.add(row);

        // Кнопка "пропустить подкатегорию"
        rows.add(List.of(button("➡ Без подкатегории", "subcat:skip")));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ================================================================
    // Admin
    // ================================================================

    public static InlineKeyboardMarkup pendingUserButtons(Long telegramId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        button("✅ Одобрить",      "approve:" + telegramId),
                        button("🚫 Заблокировать", "block_user:" + telegramId)
                ))
                .build();
    }

    public static InlineKeyboardMarkup blockedUserButtons(Long telegramId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        button("✅ Разблокировать", "unblock_user:" + telegramId)
                ))
                .build();
    }

    // ================================================================
    // Subscriptions
    // ================================================================

    public static InlineKeyboardMarkup subscriptionActions(List<Subscription> subs) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Subscription sub : subs) {
            rows.add(List.of(
                    button("🗑 Удалить «" + sub.getName() + "»", "cancel_sub:" + sub.getId())
            ));
        }
        rows.add(List.of(button("➕ Добавить подписку", "add_sub")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ================================================================
    // Statistics
    // ================================================================

    public static InlineKeyboardMarkup statsPeriod() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        button("📅 Сегодня", "stats:today"),
                        button("📆 Неделя",  "stats:week"),
                        button("🗓 Месяц",   "stats:month")
                ))
                .build();
    }

    // ================================================================
    // Helper
    // ================================================================

    private static InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}