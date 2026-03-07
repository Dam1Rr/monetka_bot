package com.monetka.insight;

import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import com.monetka.repository.UserInsightRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * УРОВЕНЬ 5 — Достижения (разовые, никогда не повторяются)
 */
@Component
public class AchievementTriggers {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
    private final TransactionRepository txRepo;
    private final UserInsightRepository insightRepo;

    public AchievementTriggers(TransactionRepository txRepo,
                               UserInsightRepository insightRepo) {
        this.txRepo      = txRepo;
        this.insightRepo = insightRepo;
    }

    public List<TriggerMessage> evaluate(User user) {
        List<TriggerMessage> result = new ArrayList<>();
        LocalDateTime now  = LocalDateTime.now(BISHKEK);
        LocalDateTime from = LocalDate.now(BISHKEK).withDayOfMonth(1).atStartOfDay();

        int month = now.getMonthValue();
        int year  = now.getYear();

        // Первый раз сэкономил > 20%
        if (!alreadySent(user, "ach_first_savings_20", 0, 0)) {
            BigDecimal income = txRepo.sumByUserAndTypeAndPeriod(
                    user, TransactionType.INCOME, from, now);
            BigDecimal spent  = txRepo.sumByUserAndTypeAndPeriod(
                    user, TransactionType.EXPENSE, from, now);
            if (income != null && income.compareTo(BigDecimal.ZERO) > 0 && spent != null) {
                BigDecimal saved = income.subtract(spent);
                int pct = saved.multiply(BigDecimal.valueOf(100))
                        .divide(income, 0, java.math.RoundingMode.HALF_UP).intValue();
                if (pct >= 20) {
                    result.add(new TriggerMessage("ach_first_savings_20",
                            "🏆 ПЕРВОЕ ДОСТИЖЕНИЕ!\n\n" +
                                    "Ты сохранил " + pct + "% дохода в этом месяце.\n" +
                                    "Большинство людей никогда этого не делают.\n" +
                                    "Это не случайность — это решение. 💪"));
                }
            }
        }

        // Первые 100 транзакций за всё время
        long totalTx = txRepo.findByUserOrderByCreatedAtDesc(user).size();
        if (totalTx >= 100 && !alreadySent(user, "ach_100_tx", 0, 0)) {
            result.add(new TriggerMessage("ach_100_tx",
                    "💯 100 транзакций!\n\n" +
                            "Ты записал каждую из них.\n" +
                            "Это дисциплина которой у большинства нет.\n" +
                            "Монетка гордится. 🎉"));
        }

        // Первый раз не потратил 3 дня подряд
        if (!alreadySent(user, "ach_3days_no_spend", 0, 0)) {
            var allTx = txRepo.findByUserAndTypeAndPeriod(
                    user, TransactionType.EXPENSE,
                    now.minusDays(3), now);
            if (allTx.isEmpty()) {
                result.add(new TriggerMessage("ach_3days_no_spend",
                        "🧘 3 дня без трат.\n\n" +
                                "Либо ты медитируешь в горах.\n" +
                                "Либо у тебя нет денег.\n" +
                                "В любом случае — это рекорд. 😄"));
            }
        }

        return result;
    }

    private boolean alreadySent(User user, String key, int month, int year) {
        // month=0, year=0 означает глобальная проверка (разовые достижения)
        if (month == 0) {
            return insightRepo.existsByUserAndTriggerKeyAndMonthAndYear(user, key, 0, 0);
        }
        return insightRepo.existsByUserAndTriggerKeyAndMonthAndYear(user, key, month, year);
    }
}