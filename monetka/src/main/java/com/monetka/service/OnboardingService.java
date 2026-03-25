package com.monetka.service;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.User;
import org.springframework.stereotype.Service;

@Service
public class OnboardingService {

    public void sendWelcome(User user, long chatId, MonetkaBot bot) {
        String name = user.getDisplayName();
        bot.sendMarkdown(chatId,
                "🎉 *Добро пожаловать в Monetka, " + name + "!*\n\n" +
                        "Я помогу тебе понять куда уходят деньги — " +
                        "без таблиц и сложных настроек.\n\n" +
                        "За 2 минуты покажу как всё работает 👇",
                KeyboardFactory.onboardingStart());
    }

    public void sendHowToRecord(long chatId, MonetkaBot bot) {
        bot.sendMarkdown(chatId,
                "💸 *Как записать расход*\n\n" +
                        "Нажми кнопку *💸 Расход* и напиши\n" +
                        "название и сумму в одном сообщении:\n\n" +
                        "  `шаурма 300`\n  `такси 500`\n  `кофе 150`\n\n" +
                        "Я сам определю категорию — " +
                        "а если не знаю, спрошу один раз " +
                        "и запомню навсегда 🧠\n\n" +
                        "Попробуй прямо сейчас — " +
                        "нажми *💸 Расход* и введи любой расход 👇",
                KeyboardFactory.onboardingTryExpense());
    }

    public void sendFinish(long chatId, MonetkaBot bot) {
        bot.sendMessage(chatId,
                "✅ *Всё готово! Поехали! 🚀*\n\n" +
                        "Что умеет Monetka:\n\n" +
                        "💸 *Расход* — записать трату\n" +
                        "💰 *Доход* — записать поступление\n" +
                        "📊 *Обзор* — куда ушли деньги\n" +
                        "🎯 *Лимиты* — бюджет по категориям\n\n" +
                        "Каждый день в 23:55 пришлю итоги дня.\n" +
                        "Каждый понедельник — сводку за неделю.\n\n" +
                        "💡 _Чем чаще пользуешься, тем умнее бот!_",
                KeyboardFactory.mainMenu());
    }

    public void sendSkip(long chatId, MonetkaBot bot) {
        bot.sendMessage(chatId,
                "Окей, разберёшься сам 😄\n\n" +
                        "Если что — /help покажет всё нужное.\n" +
                        "Кнопки внизу — твой главный инструмент 👇",
                KeyboardFactory.mainMenu());
    }

    public void sendResetOffer(long chatId, MonetkaBot bot) {
        bot.sendMarkdown(chatId,
                "🎉 *Отлично! Теперь ты знаешь как работает Monetka.*\n\n" +
                        "Тестовые записи можно удалить —\n" +
                        "и начать с чистого листа 📄",
                KeyboardFactory.onboardingReset());
    }

    public void sendAskInitialBalance(long chatId, MonetkaBot bot) {
        bot.sendMarkdown(chatId,
                "💰 *Сколько у тебя сейчас есть денег?*\n\n" +
                        "Это будет твоей точкой отсчёта.\n" +
                        "Просто напиши сумму:\n\n" +
                        "  `32000`\n  `15500`\n\n" +
                        "_Или пропусти — стартуем с 0_",
                KeyboardFactory.onboardingBalanceSkip());
    }
}
