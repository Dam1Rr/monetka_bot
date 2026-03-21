package com.monetka.scheduler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.User;
import com.monetka.service.StreakService;
import com.monetka.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Каждую ночь в 00:05 по Bishkek проверяет у кого streak прервался.
 * Если пользователь не сделал ни одной записи вчера — сбрасываем streak
 * и отправляем мотивирующее уведомление (только если streak был > 1).
 */
@Component
public class StreakResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(StreakResetScheduler.class);

    private final UserService   userService;
    private final StreakService  streakService;
    private final MonetkaBot    bot;

    public StreakResetScheduler(UserService userService,
                                StreakService streakService,
                                MonetkaBot bot) {
        this.userService  = userService;
        this.streakService = streakService;
        this.bot           = bot;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Bishkek")
    public void resetMissedStreaks() {
        List<User> users = userService.getActiveUsers();
        log.info("StreakResetScheduler: checking {} users", users.size());

        for (User user : users) {
            try {
                int prevStreak = user.getStreakDays();
                boolean wasReset = streakService.resetIfMissed(user);

                // Уведомляем только если серия была больше 1 дня
                if (wasReset && prevStreak > 1) {
                    String text = streakService.buildResetText(prevStreak);
                    bot.sendMessage(user.getTelegramId(), text, KeyboardFactory.mainMenu());
                }
            } catch (Exception e) {
                log.error("StreakReset failed for user {}: {}", user.getTelegramId(), e.getMessage());
            }
        }
    }
}