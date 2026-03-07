package com.monetka.insight;

import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.UserInsight;
import com.monetka.repository.UserInsightRepository;
import com.monetka.bot.MonetkaBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Главный оркестратор — проверяет все триггеры и отправляет сообщения
 */
@Service
public class InsightEngine {

    private static final Logger log = LoggerFactory.getLogger(InsightEngine.class);
    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private final InstantTriggers      instantTriggers;
    private final AchievementTriggers  achievementTriggers;
    private final UserInsightRepository insightRepo;

    public InsightEngine(InstantTriggers instantTriggers,
                         AchievementTriggers achievementTriggers,
                         UserInsightRepository insightRepo) {
        this.instantTriggers     = instantTriggers;
        this.achievementTriggers = achievementTriggers;
        this.insightRepo         = insightRepo;
    }

    /**
     * Вызывается после каждой записанной транзакции
     */
    @Transactional
    public void onTransaction(User user, Transaction tx, MonetkaBot bot) {
        try {
            LocalDateTime now = LocalDateTime.now(BISHKEK);
            int month = now.getMonthValue();
            int year  = now.getYear();

            // Антиспам: не более 1 триггера в день
            long todayCount = insightRepo.countRecentInsights(user, now.toLocalDate().atStartOfDay());
            if (todayCount >= 1) return;

            // Антиспам: не 3 негативных подряд — нужна похвала
            long recentNeg = insightRepo.countRecentNegativeInsights(user, now.minusDays(3));

            // Проверяем достижения (позитивные — приоритет)
            List<TriggerMessage> achievements = achievementTriggers.evaluate(user);
            for (TriggerMessage tm : achievements) {
                if (alreadySent(user, tm.key(), 0, 0)) continue;
                send(user, tm, bot, 0, 0);
                return; // Одно сообщение в день
            }

            // Проверяем мгновенные триггеры
            List<TriggerMessage> instant = instantTriggers.evaluate(user, tx);

            // Если слишком много негатива — ищем позитивный
            if (recentNeg >= 2) {
                for (TriggerMessage tm : instant) {
                    if (!tm.isNegative() && !alreadySent(user, tm.key(), month, year)) {
                        send(user, tm, bot, month, year);
                        return;
                    }
                }
                return; // Не нашли позитивный — молчим
            }

            // Отправляем первый несрабатывавший триггер
            for (TriggerMessage tm : instant) {
                if (!alreadySent(user, tm.key(), month, year)) {
                    send(user, tm, bot, month, year);
                    return;
                }
            }

        } catch (Exception e) {
            log.warn("InsightEngine error for user {}: {}", user.getId(), e.getMessage());
        }
    }

    private void send(User user, TriggerMessage tm, MonetkaBot bot, int month, int year) {
        // Не отправляем ночью (22:00 - 8:00) — кроме ночных триггеров
        int hour = LocalDateTime.now(BISHKEK).getHour();
        boolean isNightTrigger = tm.key().contains("night");
        if (!isNightTrigger && (hour >= 22 || hour < 8)) return;

        bot.sendMarkdown(user.getTelegramId(), tm.text());
        insightRepo.save(new UserInsight(user, tm.key(), month, year));
        log.info("Insight sent to user {}: {}", user.getId(), tm.key());
    }

    private boolean alreadySent(User user, String key, int month, int year) {
        return insightRepo.existsByUserAndTriggerKeyAndMonthAndYear(user, key, month, year);
    }
}