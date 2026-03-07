package com.monetka.admin;

import com.monetka.model.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * All inline keyboards used exclusively by the admin panel.
 * Callback data convention:  adm:<action>[:<param>]
 */
public final class AdminKeyboardFactory {

    private AdminKeyboardFactory() {}

    // ================================================================
    // Main menu
    // ================================================================

    public static InlineKeyboardMarkup mainMenu(boolean registrationOpen) {
        String modeLabel = registrationOpen ? "🟢 Регистрация: открытая" : "🔴 Регистрация: по заявкам";
        return InlineKeyboardMarkup.builder()
                .keyboardRow(row(btn("📥 Заявки",           "adm:pending"),
                        btn("🚫 Заблокированные",  "adm:blocked")))
                .keyboardRow(row(btn("👥 Пользователи",     "adm:users"),
                        btn("📊 Статистика",        "adm:stats")))
                .keyboardRow(row(btn("📈 Активность",        "adm:activity")))
                .keyboardRow(row(btn(modeLabel,              "adm:toggle_reg")))
                .keyboardRow(row(btn("📁 Выгрузить список", "adm:export")))
                .keyboardRow(row(btn("🧹 Очистить данные",  "adm:wipe_1")))
                .build();
    }

    // ================================================================
    // User management — per-card buttons
    // ================================================================

    /** Buttons under each PENDING user card */
    public static InlineKeyboardMarkup pendingUserActions(Long telegramId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(row(
                        btn("✅ Принять",    "adm:approve:" + telegramId),
                        btn("❌ Отклонить",  "adm:reject:"  + telegramId)
                ))
                .build();
    }

    /** Button under each BLOCKED user card */
    public static InlineKeyboardMarkup blockedUserActions(Long telegramId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(row(btn("🔓 Разблокировать", "adm:unblock:" + telegramId)))
                .build();
    }

    /** Button under each ACTIVE user card */
    public static InlineKeyboardMarkup activeUserActions(Long telegramId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(row(btn("🚫 Заблокировать", "adm:block:" + telegramId)))
                .build();
    }

    // ================================================================
    // Wipe confirmation — two-step
    // ================================================================

    /** Step 1 — first warning */
    public static InlineKeyboardMarkup wipeStep1() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(row(
                        btn("❌ Отмена",      "adm:wipe_cancel"),
                        btn("⚠️ Подтвердить", "adm:wipe_2")
                ))
                .build();
    }

    /** Step 2 — final irreversible button */
    public static InlineKeyboardMarkup wipeStep2() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(row(
                        btn("❌ Отмена",               "adm:wipe_cancel"),
                        btn("🔥 УДАЛИТЬ ВСЁ",          "adm:wipe_exec")
                ))
                .build();
    }

    // ================================================================
    // Back button
    // ================================================================

    public static InlineKeyboardMarkup backToMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(row(btn("🔙 Главное меню", "adm:menu")))
                .build();
    }

    // ================================================================
    // Helpers
    // ================================================================

    private static InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    @SafeVarargs
    private static <T> List<T> row(T... items) {
        return List.of(items);
    }
}