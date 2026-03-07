package com.monetka.service;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.User;
import org.springframework.stereotype.Service;

/**
 * Manages the onboarding flow for newly approved users.
 *
 * Flow:
 *   Step 1 — Welcome + "Поехали!" button          (sent on approval)
 *   Step 2 — How to record expense + example       (after "Поехали!")
 *   Step 3 — Suggest setting first goal            (after "onb:try_done")
 *   Step 4 — All done, show main menu              (after goal choice)
 *
 * All steps are driven by onb:* callbacks from CallbackHandler.
 */
@Service
public class OnboardingService {

    private final MonetkaBot bot;

    public OnboardingService(MonetkaBot bot) {
        this.bot = bot;
    }

    // ================================================================
    // Step 1 — sent right after admin approves user
    // ================================================================

    public void sendWelcome(User user, long chatId) {
        String name = user.getDisplayName();
        bot.sendMessage(chatId,
                "🎉 *Добро пожаловать в Monetka, " + name + "!*\n\n" +
                        "Я помогу тебе понять куда уходят деньги — " +
                        "без таблиц и сложных настроек.\n\n" +
                        "За 2 минуты покажу как всё работает 👇",
                KeyboardFactory.onboardingStart());
    }

    // ================================================================
    // Step 2 — how to record expense
    // ================================================================

    public void sendHowToRecord(long chatId) {
        bot.sendMessage(chatId,
                "💸 *Как записать расход*\n\n" +
                        "Нажми кнопку *💸 Расход* и напиши\n" +
                        "название и сумму в одном сообщении:\n\n" +
                        "  `шаурма 300`\n" +
                        "  `такси 500`\n" +
                        "  `кофе 150`\n\n" +
                        "Я сам определю категорию — " +
                        "а если не знаю, спрошу один раз " +
                        "и запомню навсегда 🧠\n\n" +
                        "Попробуй прямо сейчас — " +
                        "нажми *💸 Расход* и введи любой расход 👇",
                KeyboardFactory.onboardingTryExpense());
    }

    // ================================================================
    // Step 3 — suggest setting first goal
    // ================================================================

    public void sendSuggestGoal(long chatId) {
        bot.sendMessage(chatId,
                "🎯 *Поставь первую цель*\n\n" +
                        "Хочешь контролировать расходы?\n" +
                        "Поставь себе бюджет на категорию.\n\n" +
                        "Например: *не тратить больше 15,000 сом\n" +
                        "в месяц на еду.*\n\n" +
                        "Когда будешь близко — тихо напомню 💡\n" +
                        "Никакого спама — только когда нужно знать.",
                KeyboardFactory.onboardingGoalChoice());
    }

    // ================================================================
    // Step 4 — finish onboarding
    // ================================================================

    public void sendFinish(long chatId) {
        bot.sendMessage(chatId,
                "✅ *Всё готово! Поехали! 🚀*\n\n" +
                        "Что умеет Monetka:\n\n" +
                        "💸 *Расход* — записать трату\n" +
                        "💰 *Доход* — записать поступление\n" +
                        "📊 *Обзор* — куда ушли деньги\n" +
                        "🎯 *Цели* — бюджет по категориям\n\n" +
                        "Каждый день в 22:00 пришлю итоги дня.\n" +
                        "Каждый понедельник — сводку за неделю.\n\n" +
                        "💡 _Чем чаще пользуешься, тем умнее бот!_",
                KeyboardFactory.mainMenu());
    }

    // ================================================================
    // Called when user skips onboarding entirely
    // ================================================================

    public void sendSkip(long chatId) {
        bot.sendMessage(chatId,
                "Окей, разберёшься сам 😄\n\n" +
                        "Если что — /help покажет всё нужное.\n" +
                        "Кнопки внизу — твой главный инструмент 👇",
                KeyboardFactory.mainMenu());
    }
}