package com.monetka.insight;

import com.monetka.model.User;
import com.monetka.model.UserMonthlyProfile;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import com.monetka.repository.UserMonthlyProfileRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * УРОВЕНЬ 4 — Месячный психологический портрет
 * Вызывается 1-го числа каждого месяца
 */
@Component
public class MonthlyPortrait {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private final TransactionRepository        txRepo;
    private final UserMonthlyProfileRepository profileRepo;
    private final ScoreCalculator              scoreCalc;
    private final PersonalityDetector          detector;

    public MonthlyPortrait(TransactionRepository txRepo,
                           UserMonthlyProfileRepository profileRepo,
                           ScoreCalculator scoreCalc,
                           PersonalityDetector detector) {
        this.txRepo       = txRepo;
        this.profileRepo  = profileRepo;
        this.scoreCalc    = scoreCalc;
        this.detector     = detector;
    }

    public String build(User user) {
        LocalDateTime now   = LocalDateTime.now(BISHKEK);
        // Прошлый месяц
        int month = now.getMonthValue() == 1 ? 12 : now.getMonthValue() - 1;
        int year  = now.getMonthValue() == 1 ? now.getYear() - 1 : now.getYear();

        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        var expenses = txRepo.findByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);
        if (expenses.size() < 5) return null; // Мало данных

        ScoreCalculator.Scores scores    = scoreCalc.calculate(user, month, year);
        PersonalityDetector.PersonalityResult p = detector.detect(user, month, year, scores);

        // Сохраняем профиль
        UserMonthlyProfile profile = profileRepo
                .findByUserAndMonthAndYear(user, month, year)
                .orElse(new UserMonthlyProfile());
        profile.setUser(user);
        profile.setMonth(month);
        profile.setYear(year);
        profile.setPersonalityType(p.type());
        profile.setPeakDay(p.peakDay());
        profile.setTopCategory(p.topCategory());
        profile.setImpulseScore(scores.impulse());
        profile.setDisciplineScore(scores.discipline());
        profile.setNightPct(scores.nightPct());
        profile.setSavingsPct(scores.savingsPct());
        profileRepo.save(profile);

        // Эволюция
        List<UserMonthlyProfile> history = profileRepo
                .findByUserOrderByYearDescMonthDesc(user);
        String evolutionBlock = buildEvolution(history, p.type());

        // Инсайт месяца
        String insight = buildInsight(user, expenses, from, to, scores);

        // Пиковый день на русском
        String peakDayRu = p.peakDay() != null
                ? java.time.DayOfWeek.valueOf(p.peakDay())
                .getDisplayName(TextStyle.FULL, new Locale("ru")) : "неизвестно";

        // Топ категории
        var catStats = txRepo.sumExpensesByCategoryAndPeriod(user, from, to);
        StringBuilder catsBlock = new StringBuilder();
        for (int i = 0; i < Math.min(3, catStats.size()); i++) {
            String cat   = (String) catStats.get(i)[0];
            String emoji = (String) catStats.get(i)[1];
            BigDecimal amt = (BigDecimal) catStats.get(i)[2];
            catsBlock.append(i + 1).append(". ")
                    .append(emoji != null ? emoji + " " : "")
                    .append(cat).append(" — ")
                    .append(String.format("%,.0f", amt)).append(" сом\n");
        }

        // Месяц на русском
        String monthName = LocalDate.of(year, month, 1)
                .getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"))
                .toUpperCase();

        // Бар прогресса
        String impBar  = bar(scores.impulse());
        String discBar = bar(scores.discipline());
        String nightBar= bar(scores.nightPct());

        return "🧠 *" + monthName + " — ПСИХОЛОГИЧЕСКИЙ ПОРТРЕТ*\n\n" +
                "ТЫ — *" + PersonalityDetector.russianName(p.type()) + "* " + p.emoji() + "\n\n" +
                "━━━━━━━━━━━━━━━━━━━\n" +
                "📊 *ТВОИ СКРЫТЫЕ МЕТРИКИ*\n\n" +
                "🎲 Импульсивность:  " + scores.impulse()     + "/100  " + impBar  + "\n" +
                "🎯 Дисциплина:      " + scores.discipline()  + "/100  " + discBar + "\n" +
                "🌙 Ночной режим:    " + scores.nightPct()    + "%     " + nightBar + "\n" +
                "💰 Сохранил:        " + scores.savingsPct()  + "%\n\n" +
                "━━━━━━━━━━━━━━━━━━━\n" +
                "⏰ *ТВОЁ ВРЕМЯ ДЕНЕГ*\n\n" +
                "Пик трат: *" + peakDayRu + "*\n" +
                "Транзакций за месяц: *" + expenses.size() + "*\n\n" +
                "━━━━━━━━━━━━━━━━━━━\n" +
                "🔍 *КУДА УШЛИ ДЕНЬГИ*\n\n" +
                catsBlock +
                "\n━━━━━━━━━━━━━━━━━━━\n" +
                "💡 *ИНСАЙТ МЕСЯЦА*\n\n" +
                insight + "\n\n" +
                evolutionBlock;
    }

    private String buildInsight(User user,
                                List<com.monetka.model.Transaction> expenses,
                                LocalDateTime from, LocalDateTime to,
                                ScoreCalculator.Scores scores) {
        if (scores.nightPct() >= 40) {
            return scores.nightPct() + "% твоих трат — после 22:00.\n" +
                    "Ночью ты другой человек.\n" +
                    "Менее экономный. Но зато веселее. 🌙";
        }
        if (scores.impulse() >= 70) {
            var catStats = txRepo.sumExpensesByCategoryAndPeriod(user, from, to);
            if (!catStats.isEmpty()) {
                String topCat = (String) catStats.get(0)[0];
                BigDecimal topAmt = (BigDecimal) catStats.get(0)[2];
                return "На *" + topCat + "* ушло " +
                        String.format("%,.0f", topAmt) + " сом.\n" +
                        "Это была не слабость.\n" +
                        "Это была инвестиция в настроение. 🎭";
            }
        }
        if (scores.savingsPct() >= 25) {
            return "Ты сохранил " + scores.savingsPct() + "% дохода.\n" +
                    "Большинство людей не умеют так.\n" +
                    "Ты — умеешь. 💪";
        }
        return "Деньги — это инструмент.\n" +
                "Ты его используешь активно.\n" +
                "Монетка просто следит чтобы инструмент не заканчивался. 🔧";
    }

    private String buildEvolution(List<UserMonthlyProfile> history, String currentType) {
        if (history.size() < 2) {
            return "━━━━━━━━━━━━━━━━━━━\n" +
                    "📈 *ЭВОЛЮЦИЯ*\n\n" +
                    "Первый портрет. История начинается.\n" +
                    "Каким будешь в следующем месяце?";
        }

        StringBuilder sb = new StringBuilder("━━━━━━━━━━━━━━━━━━━\n📈 *ЭВОЛЮЦИЯ*\n\n");
        for (int i = Math.min(history.size() - 1, 2); i >= 0; i--) {
            UserMonthlyProfile h = history.get(i);
            String mName = LocalDate.of(h.getYear(), h.getMonth(), 1)
                    .getMonth().getDisplayName(TextStyle.SHORT_STANDALONE, new Locale("ru"));
            sb.append(capitalize(mName)).append(": ")
                    .append(PersonalityDetector.russianName(h.getPersonalityType()))
                    .append("\n");
        }

        // Проверяем стабильность
        long sameCount = history.stream()
                .limit(3)
                .filter(h -> currentType.equals(h.getPersonalityType()))
                .count();
        if (sameCount >= 3) {
            sb.append("\nТретий месяц подряд — ")
                    .append(PersonalityDetector.russianName(currentType)).append(".\n")
                    .append("Это уже не случайность. Это характер.");
        } else if (history.size() >= 2) {
            String prev = history.get(1).getPersonalityType();
            if (!prev.equals(currentType)) {
                // Прогресс или деградация
                boolean improved = isImprovement(prev, currentType);
                if (improved) {
                    sb.append("\n🏆 Ты эволюционировал!\n")
                            .append(PersonalityDetector.russianName(prev))
                            .append(" → ")
                            .append(PersonalityDetector.russianName(currentType))
                            .append("\nПрошлый ты бы не поверил.");
                } else {
                    sb.append("\n🤔 Что-то пошло не так...\n")
                            .append(PersonalityDetector.russianName(prev))
                            .append(" → ")
                            .append(PersonalityDetector.russianName(currentType))
                            .append("\nНо ещё не поздно исправить.");
                }
            }
        }

        sb.append("\n\n_Следующий месяц — сможешь измениться?_");
        return sb.toString();
    }

    private boolean isImprovement(String from, String to) {
        List<String> good = List.of("FUTURE_MILLIONAIRE", "ACCOUNTANT");
        List<String> bad  = List.of("CHAOS_PHILOSOPHER", "SPRINTER", "SQUIRREL_NO_NUTS");
        if (good.contains(to) && !good.contains(from)) return true;
        if (bad.contains(from) && !bad.contains(to))   return true;
        return false;
    }

    private String bar(int value) {
        int filled = Math.min(10, value / 10);
        return "█".repeat(filled) + "░".repeat(10 - filled);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}