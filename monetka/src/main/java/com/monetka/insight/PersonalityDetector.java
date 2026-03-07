package com.monetka.insight;

import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PersonalityDetector {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
    private final TransactionRepository txRepo;

    public PersonalityDetector(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    public PersonalityResult detect(User user, int month, int year,
                                    ScoreCalculator.Scores scores) {
        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        var expenses = txRepo.findByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);
        if (expenses.isEmpty()) return new PersonalityResult("UNKNOWN", "❓", null, null);

        // Peak day of week
        Map<DayOfWeek, Long> byDay = new HashMap<>();
        for (var t : expenses) {
            DayOfWeek d = t.getCreatedAt().getDayOfWeek();
            byDay.merge(d, 1L, Long::sum);
        }
        DayOfWeek peakDay = byDay.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);

        // Peak hour
        Map<Integer, Long> byHour = new HashMap<>();
        for (var t : expenses) byHour.merge(t.getCreatedAt().getHour(), 1L, Long::sum);
        int peakHour = byHour.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(12);

        // Top category
        var catStats = txRepo.sumExpensesByCategoryAndPeriod(user, from, to);
        String topCat = catStats.isEmpty() ? null : (String) catStats.get(0)[0];
        BigDecimal topCatTotal = catStats.isEmpty() ? BigDecimal.ZERO : (BigDecimal) catStats.get(0)[2];
        BigDecimal allTotal = expenses.stream().map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int topCatPct = allTotal.compareTo(BigDecimal.ZERO) > 0
                ? topCatTotal.multiply(BigDecimal.valueOf(100))
                .divide(allTotal, 0, java.math.RoundingMode.HALF_UP).intValue()
                : 0;

        // Sprinter — 50%+ денег в первые 5 дней
        BigDecimal first5 = expenses.stream()
                .filter(t -> t.getCreatedAt().getDayOfMonth() <= 5)
                .map(t -> t.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        int first5Pct = allTotal.compareTo(BigDecimal.ZERO) > 0
                ? first5.multiply(BigDecimal.valueOf(100))
                .divide(allTotal, 0, java.math.RoundingMode.HALF_UP).intValue()
                : 0;

        // Friday spender
        long fridayTx = expenses.stream()
                .filter(t -> t.getCreatedAt().getDayOfWeek() == DayOfWeek.FRIDAY).count();
        int fridayPct = (int)(fridayTx * 100 / expenses.size());

        // Delivery check
        boolean isDelivery = topCat != null && (
                topCat.toLowerCase().contains("доставка") ||
                        topCat.toLowerCase().contains("еда") ||
                        topCat.toLowerCase().contains("кафе"));

        // Tsunami — тихо + один большой выброс
        BigDecimal avg = allTotal.divide(BigDecimal.valueOf(expenses.size()), 2, java.math.RoundingMode.HALF_UP);
        long tsunamiTx = expenses.stream()
                .filter(t -> t.getAmount().compareTo(avg.multiply(BigDecimal.valueOf(5))) > 0).count();

        // Определяем тип
        String type;
        if (first5Pct >= 50)                                          type = "SPRINTER";
        else if (scores.nightPct() >= 50)                             type = "NIGHT_DRACULA";
        else if (fridayPct >= 40)                                     type = "FRIDAY_ZOMBIE";
        else if (tsunamiTx >= 1 && scores.impulse() >= 60)            type = "TSUNAMI";
        else if (isDelivery && topCatPct >= 35)                       type = "DELIVERY_KING";
        else if (scores.discipline() >= 70 && scores.savingsPct() >= 20) type = "FUTURE_MILLIONAIRE";
        else if (scores.discipline() >= 70)                           type = "ACCOUNTANT";
        else if (scores.impulse() >= 70)                              type = "CHAOS_PHILOSOPHER";
        else if (scores.savingsPct() < 5 && scores.impulse() >= 40)  type = "SQUIRREL_NO_NUTS";
        else                                                           type = "TSUNAMI";

        String peakDayStr = peakDay != null ? peakDay.name() : null;
        return new PersonalityResult(type, emoji(type), peakDayStr, topCat);
    }

    private String emoji(String type) {
        return switch (type) {
            case "NIGHT_DRACULA"     -> "🧛";
            case "FRIDAY_ZOMBIE"     -> "🧟";
            case "TSUNAMI"           -> "🌊";
            case "DELIVERY_KING"     -> "👑";
            case "FUTURE_MILLIONAIRE"-> "🏆";
            case "ACCOUNTANT"        -> "🧮";
            case "CHAOS_PHILOSOPHER" -> "🎲";
            case "SPRINTER"          -> "🏃";
            case "SQUIRREL_NO_NUTS"  -> "🐿️";
            default                  -> "❓";
        };
    }

    public static String russianName(String type) {
        return switch (type) {
            case "NIGHT_DRACULA"     -> "Ночной Дракула";
            case "FRIDAY_ZOMBIE"     -> "Зомби Пятницы";
            case "TSUNAMI"           -> "Цунами";
            case "DELIVERY_KING"     -> "Король Доставки";
            case "FUTURE_MILLIONAIRE"-> "Будущий Миллионер";
            case "ACCOUNTANT"        -> "Бухгалтер в Душе";
            case "CHAOS_PHILOSOPHER" -> "Философ Хаоса";
            case "SPRINTER"          -> "Спринтер";
            case "SQUIRREL_NO_NUTS"  -> "Белка без Орехов";
            default                  -> "Загадка";
        };
    }

    public record PersonalityResult(String type, String emoji,
                                    String peakDay, String topCategory) {}
}