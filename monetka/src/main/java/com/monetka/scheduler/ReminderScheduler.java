package com.monetka.scheduler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.UserReminder;
import com.monetka.service.ReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Каждый час проверяет кому нужно отправить напоминание.
 * Утреннее (по умолчанию 13:00) и вечернее (по умолчанию 21:00).
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private final ReminderService reminderService;
    private final MonetkaBot       bot;

    public ReminderScheduler(ReminderService reminderService, MonetkaBot bot) {
        this.reminderService = reminderService;
        this.bot             = bot;
    }

    // Каждый час в начале часа — Asia/Bishkek
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Bishkek")
    public void sendReminders() {
        int hour = LocalDateTime.now(BISHKEK).getHour();

        // Утренние
        List<UserReminder> morning = reminderService.getMorningAt(hour);
        if (!morning.isEmpty()) {
            log.info("Sending morning reminders to {} users at {}:00", morning.size(), hour);
            for (UserReminder r : morning) {
                try {
                    sendMorning(r);
                } catch (Exception e) {
                    log.error("Morning reminder failed for user {}: {}", r.getUser().getTelegramId(), e.getMessage());
                }
            }
        }

        // Вечерние
        List<UserReminder> evening = reminderService.getEveningAt(hour);
        if (!evening.isEmpty()) {
            log.info("Sending evening reminders to {} users at {}:00", evening.size(), hour);
            for (UserReminder r : evening) {
                try {
                    sendEvening(r);
                } catch (Exception e) {
                    log.error("Evening reminder failed for user {}: {}", r.getUser().getTelegramId(), e.getMessage());
                }
            }
        }
    }

    private void sendMorning(UserReminder r) {
        long chatId = r.getUser().getTelegramId();
        String[] tips = {
                "💡 _Совет дня: записывай траты сразу — память обманывает._",
                "💡 _Совет дня: маленькие траты складываются в большие суммы._",
                "💡 _Совет дня: знать куда уходят деньги — первый шаг к контролю._",
                "💡 _Совет дня: поставь лимит на кофе и такси — удивишься сколько сэкономишь._",
        };
        String tip = tips[(int)(System.currentTimeMillis() / 3600000 % tips.length)];
        String text =
                "☀️ *Доброе утро!* Не забудь записать утренние траты 📝\n\n" +
                        "_Кофе, такси, завтрак — всё записывается за секунды._\n\n" +
                        tip;
        bot.sendMessage(chatId, text, KeyboardFactory.mainMenu());
    }

    private void sendEvening(UserReminder r) {
        long chatId = r.getUser().getTelegramId();
        String[] insights = {
                "💡 _Инсайт: люди которые записывают траты тратят на 20% меньше._",
                "💡 _Инсайт: подведи итог дня — это займёт 2 минуты но даст ясность._",
                "💡 _Инсайт: если сегодня перерасход — завтра скорректируй план._",
                "💡 _Инсайт: регулярный учёт трат — лучшая привычка для финансового здоровья._",
        };
        String insight = insights[(int)(System.currentTimeMillis() / 3600000 % insights.length)];
        String text =
                "🌙 *Итог дня* — не забудь записать всё что потратил сегодня!\n\n" +
                        "_Пара минут сейчас = ясная картина финансов завтра._\n\n" +
                        insight;
        bot.sendMessage(chatId, text, KeyboardFactory.mainMenu());
    }
}