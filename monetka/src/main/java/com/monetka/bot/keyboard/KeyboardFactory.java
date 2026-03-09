package com.monetka.bot.keyboard;

import com.monetka.model.*;
import com.monetka.repository.CategoryRepository;
import com.monetka.service.StatisticsService;
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
        KeyboardRow r1 = new KeyboardRow(); r1.add("💸 Расход"); r1.add("💰 Доход");
        KeyboardRow r2 = new KeyboardRow(); r2.add("📅 Сегодня"); r2.add("📆 Неделя");
        KeyboardRow r3 = new KeyboardRow(); r3.add("🗓 Месяц");   r3.add("🎯 Лимиты");
        kb.setKeyboard(List.of(r1, r2, r3));
        return kb;
    }

    // ================================================================
    // Overview — tab navbar (inline, always shown under overview msgs)
    // ================================================================

    // ── Period picker shown under stats messages ──
    public static InlineKeyboardMarkup periodPicker() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        btn("📅 Сегодня", "stats:today"),
                        btn("📆 7 дней",  "stats:week"),
                        btn("🗓 Месяц",   "stats:month")
                ))
                .keyboardRow(List.of(btn("📅 Выбрать даты...", "stats:cal")))
                .build();
    }

    // ── Calendar grid ──
    public static InlineKeyboardMarkup calendarMonth(int year, int month,
                                                     Integer startDay, Integer endDay) {
        java.time.LocalDate first = java.time.LocalDate.of(year, month, 1);
        int daysInMonth = first.lengthOfMonth();
        int startDow    = first.getDayOfWeek().getValue(); // 1=Mon

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                btn("‹", "stats:cal:prev:" + year + ":" + month),
                btn(first.getMonth().getDisplayName(java.time.format.TextStyle.FULL_STANDALONE,
                        new java.util.Locale("ru")).toUpperCase() + " " + year, "noop"),
                btn("›", "stats:cal:next:" + year + ":" + month)
        ));
        rows.add(List.of(btn("Пн","noop"),btn("Вт","noop"),btn("Ср","noop"),btn("Чт","noop"),
                btn("Пт","noop"),btn("Сб","noop"),btn("Вс","noop")));

        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int i = 1; i < startDow; i++) row.add(btn(" ", "noop"));
        for (int d = 1; d <= daysInMonth; d++) {
            String label;
            if (Integer.valueOf(d).equals(startDay) || Integer.valueOf(d).equals(endDay))
                label = "·" + d + "·";
            else if (startDay != null && endDay != null && d > startDay && d < endDay)
                label = "•" + d + "•";
            else label = String.valueOf(d);
            row.add(btn(label, "stats:cal:day:" + year + ":" + month + ":" + d));
            if (row.size() == 7) { rows.add(new ArrayList<>(row)); row.clear(); }
        }
        if (!row.isEmpty()) {
            while (row.size() < 7) row.add(btn(" ", "noop"));
            rows.add(row);
        }
        if (startDay != null && endDay != null) {
            String mon = first.getMonth().getDisplayName(
                    java.time.format.TextStyle.SHORT_STANDALONE, new java.util.Locale("ru"));
            rows.add(List.of(btn("✅ Показать " + startDay + "–" + endDay + " " + mon,
                    "stats:cal:confirm:" + year + ":" + month + ":" + startDay + ":" + endDay)));
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
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
    // Overview — main screen
    // ================================================================

    public static InlineKeyboardMarkup overviewMain(
            List<StatisticsService.CategoryStats> cats,
            CategoryRepository categoryRepository) {

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Category buttons (top 8)
        int show = Math.min(cats.size(), 8);
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int i = 0; i < show; i++) {
            StatisticsService.CategoryStats cs = cats.get(i);
            String name = cs.label.replaceAll("^[\\p{So}\\p{Sm}\\s]+", "").trim();
            categoryRepository.findByName(name).ifPresent(cat ->
                    row.add(btn(cs.label, "overview:cat:" + cat.getId())));
            if (row.size() == 2) {
                rows.add(new ArrayList<>(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) rows.add(new ArrayList<>(row));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ================================================================
    // Overview — category screen
    // ================================================================

    public static InlineKeyboardMarkup overviewCategory(long categoryId,
                                                        boolean hasSubs,
                                                        boolean hasGoal) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("📅 По дням", "overview:days:" + categoryId)));

        if (hasGoal) {
            rows.add(List.of(
                    btn("✏️ Изменить цель",  "overview:set_goal:" + categoryId),
                    btn("🗑 Удалить цель",   "overview:del_goal:" + categoryId)
            ));
        } else {
            rows.add(List.of(btn("🎯 Поставить цель", "overview:set_goal:" + categoryId)));
        }
        rows.add(List.of(btn("← Назад", "overview:main")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ================================================================
    // Overview — subcategory screen
    // ================================================================

    public static InlineKeyboardMarkup overviewSubcategory(long subcategoryId,
                                                           Long categoryId,
                                                           List<Transaction> txs) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Last 3 transactions — view/edit buttons
        int show = Math.min(txs.size(), 3);
        for (int i = 0; i < show; i++) {
            Transaction tx = txs.get(i);
            String label = tx.getDescription() + " −" + String.format("%,.0f", tx.getAmount());
            rows.add(List.of(btn("✏️ " + label, "overview:view_tx:" + tx.getId())));
        }

        if (categoryId != null)
            rows.add(List.of(btn("← Назад", "overview:cat:" + categoryId)));
        else
            rows.add(List.of(btn("← Назад", "overview:main")));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ================================================================
    // Overview — goals screen
    // ================================================================

    public static InlineKeyboardMarkup overviewGoals(List<BudgetGoal> existing,
                                                     List<Category> allCats) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Existing goals — edit / delete
        for (BudgetGoal g : existing) {
            rows.add(List.of(
                    btn("✏️ " + (g.getCategory().getEmoji() != null ? g.getCategory().getEmoji() + " " : "") + g.getCategory().getName(), "overview:set_goal:" + g.getCategory().getId()),
                    btn("🗑", "overview:del_goal:" + g.getCategory().getId())
            ));
        }

        // Add goal for categories without one
        List<Long> existingIds = existing.stream()
                .map(g -> g.getCategory().getId()).toList();

        List<InlineKeyboardButton> addRow = new ArrayList<>();
        for (Category cat : allCats) {
            if (!existingIds.contains(cat.getId())) {
                String label = (cat.getEmoji() != null ? cat.getEmoji() + " " : "") + cat.getName();
                String catLabel = (cat.getEmoji() != null ? cat.getEmoji() + " " : "") + cat.getName();
                addRow.add(btn(catLabel, "overview:set_goal:" + cat.getId()));
                if (addRow.size() == 2) {
                    rows.add(new ArrayList<>(addRow));
                    addRow.clear();
                }
            }
        }
        if (!addRow.isEmpty()) rows.add(new ArrayList<>(addRow));

        rows.add(List.of(btn("← Назад", "overview:main")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ================================================================
    // Back to category
    // ================================================================

    public static InlineKeyboardMarkup backToCategory(long categoryId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        btn("← Назад", "overview:cat:" + categoryId)
                ))
                .build();
    }

    // ================================================================
    // Confirm delete transaction
    // ================================================================

    public static InlineKeyboardMarkup confirmDeleteTx(long txId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        btn("✅ Да, удалить", "overview:confirm_del:" + txId),
                        btn("← Отмена",       "overview:main")
                ))
                .build();
    }

    // ================================================================
    // Category choice — manual selection
    // ================================================================

    public static InlineKeyboardMarkup categoryChoice(List<Category> categories) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (Category cat : categories) {
            row.add(btn(cat.getDisplayName(), "cat:" + cat.getId()));
            if (row.size() == 2) { rows.add(new ArrayList<>(row)); row.clear(); }
        }
        if (!row.isEmpty()) rows.add(row);
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup subcategoryChoice(List<Subcategory> subs, long categoryId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (Subcategory sub : subs) {
            row.add(btn(sub.getDisplayName(), "subcat:" + sub.getId()));
            if (row.size() == 2) { rows.add(new ArrayList<>(row)); row.clear(); }
        }
        if (!row.isEmpty()) rows.add(row);
        rows.add(List.of(btn("➡ Без подкатегории", "subcat:skip")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ================================================================
    // Admin
    // ================================================================

    public static InlineKeyboardMarkup pendingUserButtons(Long telegramId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        btn("✅ Одобрить",      "approve:" + telegramId),
                        btn("🚫 Заблокировать", "block_user:" + telegramId)
                ))
                .build();
    }

    public static InlineKeyboardMarkup blockedUserButtons(Long telegramId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(btn("✅ Разблокировать", "unblock_user:" + telegramId)))
                .build();
    }

    // ================================================================
    // Subscriptions
    // ================================================================

    public static InlineKeyboardMarkup subscriptionActions(List<Subscription> subs) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Subscription sub : subs) {
            rows.add(List.of(btn("🗑 Удалить «" + sub.getName() + "»", "cancel_sub:" + sub.getId())));
        }
        rows.add(List.of(btn("➕ Добавить подписку", "add_sub")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }


    // ================================================================
    // Onboarding
    // ================================================================

    public static InlineKeyboardMarkup onboardingStart() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        btn("🚀 Поехали!", "onb:step2"),
                        btn("Пропустить ➡",  "onb:skip")
                ))
                .build();
    }

    public static InlineKeyboardMarkup onboardingTryExpense() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(btn("Пропустить ➡", "onb:step3")))
                .build();
    }

    public static InlineKeyboardMarkup onboardingGoalChoice() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        btn("🎯 Поставить цель", "onb:goals"),
                        btn("Позже ➡",           "onb:finish")
                ))
                .build();
    }

    // ================================================================
    // Edit transaction
    // ================================================================

    public static InlineKeyboardMarkup editTxOptions(long txId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        btn("💸 Изменить сумму",    "overview:edit_amount:" + txId),
                        btn("📝 Изменить описание", "overview:edit_desc:" + txId)
                ))
                .keyboardRow(List.of(
                        btn("🗑 Удалить",    "overview:del_tx:" + txId),
                        btn("← Назад",      "overview:main")
                ))
                .build();
    }

    // ================================================================
    // Helper
    // ================================================================

    private static InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}