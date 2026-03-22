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
                "\uD83C\uDF89 *\u0414\u043e\u0431\u0440\u043e \u043f\u043e\u0436\u0430\u043b\u043e\u0432\u0430\u0442\u044c \u0432 Monetka, " + name + "!*\n\n" +
                        "\u042f \u043f\u043e\u043c\u043e\u0433\u0443 \u0442\u0435\u0431\u0435 \u043f\u043e\u043d\u044f\u0442\u044c \u043a\u0443\u0434\u0430 \u0443\u0445\u043e\u0434\u044f\u0442 \u0434\u0435\u043d\u044c\u0433\u0438 \u2014 " +
                        "\u0431\u0435\u0437 \u0442\u0430\u0431\u043b\u0438\u0446 \u0438 \u0441\u043b\u043e\u0436\u043d\u044b\u0445 \u043d\u0430\u0441\u0442\u0440\u043e\u0435\u043a.\n\n" +
                        "\u0417\u0430 2 \u043c\u0438\u043d\u0443\u0442\u044b \u043f\u043e\u043a\u0430\u0436\u0443 \u043a\u0430\u043a \u0432\u0441\u0451 \u0440\u0430\u0431\u043e\u0442\u0430\u0435\u0442 \uD83D\uDC47",
                KeyboardFactory.onboardingStart());
    }

    public void sendHowToRecord(long chatId, MonetkaBot bot) {
        bot.sendMarkdown(chatId,
                "\uD83D\uDCB8 *\u041a\u0430\u043a \u0437\u0430\u043f\u0438\u0441\u0430\u0442\u044c \u0440\u0430\u0441\u0445\u043e\u0434*\n\n" +
                        "\u041d\u0430\u0436\u043c\u0438 \u043a\u043d\u043e\u043f\u043a\u0443 *\uD83D\uDCB8 \u0420\u0430\u0441\u0445\u043e\u0434* \u0438 \u043d\u0430\u043f\u0438\u0448\u0438\n" +
                        "\u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 \u0438 \u0441\u0443\u043c\u043c\u0443 \u0432 \u043e\u0434\u043d\u043e\u043c \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0438:\n\n" +
                        "  `\u0448\u0430\u0443\u0440\u043c\u0430 300`\n  `\u0442\u0430\u043a\u0441\u0438 500`\n  `\u043a\u043e\u0444\u0435 150`\n\n" +
                        "\u042f \u0441\u0430\u043c \u043e\u043f\u0440\u0435\u0434\u0435\u043b\u044e \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044e \u2014 " +
                        "\u0430 \u0435\u0441\u043b\u0438 \u043d\u0435 \u0437\u043d\u0430\u044e, \u0441\u043f\u0440\u043e\u0448\u0443 \u043e\u0434\u0438\u043d \u0440\u0430\u0437 " +
                        "\u0438 \u0437\u0430\u043f\u043e\u043c\u043d\u044e \u043d\u0430\u0432\u0441\u0435\u0433\u0434\u0430 \uD83E\uDDE0\n\n" +
                        "\u041f\u043e\u043f\u0440\u043e\u0431\u0443\u0439 \u043f\u0440\u044f\u043c\u043e \u0441\u0435\u0439\u0447\u0430\u0441 \u2014 " +
                        "\u043d\u0430\u0436\u043c\u0438 *\uD83D\uDCB8 \u0420\u0430\u0441\u0445\u043e\u0434* \u0438 \u0432\u0432\u0435\u0434\u0438 \u043b\u044e\u0431\u043e\u0439 \u0440\u0430\u0441\u0445\u043e\u0434 \uD83D\uDC47",
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
                "\u041e\u043a\u0435\u0439, \u0440\u0430\u0437\u0431\u0435\u0440\u0451\u0448\u044c\u0441\u044f \u0441\u0430\u043c \uD83D\uDE04\n\n" +
                        "\u0415\u0441\u043b\u0438 \u0447\u0442\u043e \u2014 /help \u043f\u043e\u043a\u0430\u0436\u0435\u0442 \u0432\u0441\u0451 \u043d\u0443\u0436\u043d\u043e\u0435.\n" +
                        "\u041a\u043d\u043e\u043f\u043a\u0438 \u0432\u043d\u0438\u0437\u0443 \u2014 \u0442\u0432\u043e\u0439 \u0433\u043b\u0430\u0432\u043d\u044b\u0439 \u0438\u043d\u0441\u0442\u0440\u0443\u043c\u0435\u043d\u0442 \uD83D\uDC47",
                KeyboardFactory.mainMenu());
    }

    public void sendResetOffer(long chatId, MonetkaBot bot) {
        bot.sendMarkdown(chatId,
                "\uD83C\uDF89 *\u041e\u0442\u043b\u0438\u0447\u043d\u043e! \u0422\u0435\u043f\u0435\u0440\u044c \u0442\u044b \u0437\u043d\u0430\u0435\u0448\u044c \u043a\u0430\u043a \u0440\u0430\u0431\u043e\u0442\u0430\u0435\u0442 Monetka.*\n\n" +
                        "\u0422\u0435\u0441\u0442\u043e\u0432\u044b\u0435 \u0437\u0430\u043f\u0438\u0441\u0438 \u043c\u043e\u0436\u043d\u043e \u0443\u0434\u0430\u043b\u0438\u0442\u044c \u2014\n" +
                        "\u0438 \u043d\u0430\u0447\u0430\u0442\u044c \u0441 \u0447\u0438\u0441\u0442\u043e\u0433\u043e \u043b\u0438\u0441\u0442\u0430 \uD83D\uDCC4",
                KeyboardFactory.onboardingReset());
    }

    public void sendAskInitialBalance(long chatId, MonetkaBot bot) {
        bot.sendMarkdown(chatId,
                "\uD83D\uDCB0 *\u0421\u043a\u043e\u043b\u044c\u043a\u043e \u0443 \u0442\u0435\u0431\u044f \u0441\u0435\u0439\u0447\u0430\u0441 \u0435\u0441\u0442\u044c \u0434\u0435\u043d\u0435\u0433?*\n\n" +
                        "\u042d\u0442\u043e \u0431\u0443\u0434\u0435\u0442 \u0442\u0432\u043e\u0435\u0439 \u0442\u043e\u0447\u043a\u043e\u0439 \u043e\u0442\u0441\u0447\u0451\u0442\u0430.\n" +
                        "\u041f\u0440\u043e\u0441\u0442\u043e \u043d\u0430\u043f\u0438\u0448\u0438 \u0441\u0443\u043c\u043c\u0443:\n\n" +
                        "  `32000`\n  `15500`\n\n" +
                        "_\u0418\u043b\u0438 \u043f\u0440\u043e\u043f\u0443\u0441\u0442\u0438 \u2014 \u0441\u0442\u0430\u0440\u0442\u0443\u0435\u043c \u0441 0_",
                KeyboardFactory.onboardingBalanceSkip());
    }
}