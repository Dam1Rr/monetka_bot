package com.monetka.service;

import com.monetka.model.User;
import com.monetka.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Управляет streak — серией дней подряд с записями.
 *
 * Правила:
 *  - Первая транзакция дня увеличивает streak на 1.
 *  - Повторные транзакции в тот же день streak не меняют.
 *  - Пропуск дня сбрасывает streak в 0 (через StreakResetScheduler).
 *  - max_streak_days не убывает никогда.
 */
@Service
public class StreakService {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    // Milestone дни для поздравлений
    private static final int[] MILESTONES = {3, 7, 14, 30, 50, 100};

    private final UserRepository userRepository;

    public StreakService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Вызывается после каждой транзакции (расход или доход).
     * Возвращает результат для формирования сообщения пользователю.
     */
    @Transactional
    public StreakResult onActivity(User user) {
        LocalDate today = LocalDate.now(BISHKEK);
        LocalDate last  = user.getLastActivityDate();

        // Уже записывали сегодня — streak не трогаем
        if (today.equals(last)) {
            return new StreakResult(user.getStreakDays(), false, false);
        }

        // Первая активность сегодня — увеличиваем
        int newStreak = user.getStreakDays() + 1;
        user.setStreakDays(newStreak);
        user.setLastActivityDate(today);

        // Обновляем рекорд
        if (newStreak > user.getMaxStreakDays()) {
            user.setMaxStreakDays(newStreak);
        }

        userRepository.save(user);

        boolean isMilestone = isMilestone(newStreak);
        return new StreakResult(newStreak, true, isMilestone);
    }

    /**
     * Сброс streak для одного пользователя (вызывает scheduler).
     * Возвращает true если streak был > 0 (надо уведомить).
     */
    @Transactional
    public boolean resetIfMissed(User user) {
        LocalDate today     = LocalDate.now(BISHKEK);
        LocalDate yesterday = today.minusDays(1);
        LocalDate last      = user.getLastActivityDate();

        // Если вчера была активность — всё ок, streak живёт
        if (last != null && (last.equals(yesterday) || last.equals(today))) {
            return false;
        }

        // Пропустил — сбрасываем
        if (user.getStreakDays() > 0) {
            user.setStreakDays(0);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    // ── Текст streak для подтверждения транзакции ──

    public String buildStreakLine(StreakResult result) {
        if (!result.isFirstToday()) return "";

        int days = result.days();
        if (days == 1) {
            return "\n\n🌱 Первый день серии — начало большого пути!";
        }
        if (result.isMilestone()) {
            return "\n\n" + milestoneText(days);
        }
        return "\n\n🔥 Серия: *" + days + " " + dayWord(days) + " подряд!*";
    }

    public String buildResetText(int lostStreak) {
        if (lostStreak <= 0) return "";
        return "😔 *Серия прервана*\n\n" +
                "Вчера не было записей — серия сброшена.\n" +
                "Была серия: " + lostStreak + " " + dayWord(lostStreak) + ".\n\n" +
                "_Начни сегодня заново — первый шаг уже сделан! 🌱_";
    }

    // ── Helpers ──

    private boolean isMilestone(int days) {
        for (int m : MILESTONES) if (m == days) return true;
        return false;
    }

    private String milestoneText(int days) {
        String emoji = switch (days) {
            case 3   -> "🌱";
            case 7   -> "🔥";
            case 14  -> "⚡";
            case 30  -> "🏆";
            case 50  -> "💎";
            case 100 -> "👑";
            default  -> "🎯";
        };
        String msg = switch (days) {
            case 3  -> "3 дня подряд — привычка начинает формироваться!";
            case 7  -> "Целая неделя без пропуска! Ты уже лучше понимаешь куда уходят деньги 💪";
            case 14 -> "2 недели подряд! Финансовый контроль становится твоей нормой.";
            case 30 -> "МЕСЯЦ БЕЗ ПРОПУСКА! Это уже настоящая финансовая дисциплина. Гордись!";
            case 50 -> "50 дней! Ты в топе самых дисциплинированных пользователей Monetka.";
            case 100 -> "100 ДНЕЙ! Легенда. Таких пользователей единицы в мире.";
            default -> days + " дней подряд — отличный результат!";
        };
        return emoji + " *" + msg + "*\n🔥 Серия: *" + days + " " + dayWord(days) + "*";
    }

    private String dayWord(int n) {
        int mod10  = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return "день";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return "дня";
        return "дней";
    }

    // ── Result record ──

    public record StreakResult(int days, boolean isFirstToday, boolean isMilestone) {}
}