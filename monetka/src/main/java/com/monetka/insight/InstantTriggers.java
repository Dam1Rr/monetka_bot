package com.monetka.insight;

import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * УРОВЕНЬ 1 — Моментальные триггеры после каждой транзакции
 */
@Component
public class InstantTriggers {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
    private final TransactionRepository txRepo;

    public InstantTriggers(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    public List<TriggerMessage> evaluate(User user, Transaction lastTx) {
        List<TriggerMessage> result = new ArrayList<>();

        LocalDateTime now      = LocalDateTime.now(BISHKEK);
        LocalDateTime from     = LocalDate.now(BISHKEK).withDayOfMonth(1).atStartOfDay();
        LocalDateTime todayStart = LocalDate.now(BISHKEK).atStartOfDay();

        var monthExpenses = txRepo.findByUserAndTypeAndPeriod(
                user, TransactionType.EXPENSE, from, now);
        var todayExpenses = txRepo.findByUserAndTypeAndPeriod(
                user, TransactionType.EXPENSE, todayStart, now);

        if (monthExpenses.isEmpty()) return result;

        // ── Ночная трата (после 23:00) ───────────────────────────
        // Конвертируем в Bishkek время (хранится как LocalDateTime в Asia/Bishkek)
        int hour = lastTx.getCreatedAt().getHour(); // createdAt уже в Bishkek после фикса prePersist
        if (hour >= 23 || hour < 4) {
            long nightDaysInRow = countNightDaysInRow(monthExpenses);
            if (nightDaysInRow == 1) {
                result.add(new TriggerMessage("neg_night_single",
                        "🌙 " + now.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM")) +
                                ", " + hour + ":0" + lastTx.getCreatedAt().getMinute() + "\n\n" +
                                "Монетка тоже не спит.\n" +
                                "Ночные траты — самые загадочные.\n" +
                                "Утром сам удивишься зачем. 🤔"));
            } else if (nightDaysInRow == 3) {
                result.add(new TriggerMessage("neg_night_3days",
                        "🧛 Третья ночь подряд.\n\n" +
                                "Ты превращаешься в Ночного Дракулу.\n" +
                                "Покупки после полуночи это отдельная личность.\n" +
                                "Она очень любит тратить."));
            } else if (nightDaysInRow >= 5) {
                result.add(new TriggerMessage("neg_night_5days",
                        "🧛 Пять ночей подряд.\n\n" +
                                "Ты официально Ночной Дракула.\n" +
                                "Днём — нормальный человек.\n" +
                                "Ночью — кошмар бюджета."));
            }
        }

        // ── Пятница вечер ────────────────────────────────────────
        if (lastTx.getCreatedAt().getDayOfWeek() == DayOfWeek.FRIDAY && hour >= 18) {
            long fridayCount = monthExpenses.stream()
                    .filter(t -> t.getCreatedAt().getDayOfWeek() == DayOfWeek.FRIDAY)
                    .map(t -> t.getCreatedAt().toLocalDate()).distinct().count();
            if (fridayCount == 2) {
                result.add(new TriggerMessage("neg_friday_2",
                        "📅 Вторая пятница подряд.\n\n" +
                                "Паттерн обнаружен: ты тратишь в пятницу\n" +
                                "как будто завтра конец света.\n" +
                                "Завтра суббота. Не конец света. Но близко. 😅"));
            } else if (fridayCount >= 3) {
                result.add(new TriggerMessage("neg_friday_3",
                        "🧟 Зомби Пятницы активирован.\n\n" +
                                "Каждую пятницу одно и то же.\n" +
                                "Ты знаешь. Монетка знает.\n" +
                                "Пятница тоже знает."));
            }
        }

        // ── Спринтер — 50% бюджета за первые 5 дней ─────────────
        int dayOfMonth = now.getDayOfMonth();
        if (dayOfMonth <= 5) {
            BigDecimal income = txRepo.sumByUserAndTypeAndPeriod(
                    user, TransactionType.INCOME, from, now);
            if (income != null && income.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal spent = txRepo.sumByUserAndTypeAndPeriod(
                        user, TransactionType.EXPENSE, from, now);
                int pct = spent.multiply(BigDecimal.valueOf(100))
                        .divide(income, 0, java.math.RoundingMode.HALF_UP).intValue();
                if (pct >= 50) {
                    result.add(new TriggerMessage("neg_sprinter_50",
                            "🏃 " + pct + "% бюджета за " + dayOfMonth + " дней.\n\n" +
                                    "Впереди ещё " + (31 - dayOfMonth) + " дней.\n" +
                                    "Монетка верит в тебя.\n" +
                                    "Монетка немного переживает. 🙂"));
                }
            }
        }

        // ── Доставка 5 дней подряд ───────────────────────────────
        long deliveryDaysInRow = countCategoryDaysInRow(monthExpenses, "еда", "доставка");
        if (deliveryDaysInRow == 5) {
            result.add(new TriggerMessage("neg_delivery_5days",
                    "👑 Пять дней подряд — доставка.\n\n" +
                            "Курьеры знают твой домофон.\n" +
                            "Они спрашивают как ты.\n" +
                            "Это уже отношения."));
        } else if (deliveryDaysInRow == 10) {
            result.add(new TriggerMessage("neg_delivery_10days",
                    "👑🔥 Десять дней доставки подряд.\n\n" +
                            "Ты кормишь курьеров всего района.\n" +
                            "Они купили на твои деньги новые велосипеды.\n" +
                            "Инвестиция в людей — это благородно."));
        }

        // ── Огромная трата — в 5 раз больше среднего ────────────
        if (monthExpenses.size() >= 5) {
            BigDecimal total = monthExpenses.stream().map(t -> t.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = total.divide(
                    BigDecimal.valueOf(monthExpenses.size()), 2, java.math.RoundingMode.HALF_UP);
            if (lastTx.getAmount().compareTo(avg.multiply(BigDecimal.valueOf(5))) > 0) {
                result.add(new TriggerMessage("neg_impulse_big",
                        "💥 Это была не покупка.\n\n" +
                                "Это была катастрофа.\n" +
                                String.format("%,.0f", lastTx.getAmount()) + " сом за один раз.\n" +
                                "Среднее по месяцу: " + String.format("%,.0f", avg) + " сом.\n" +
                                "Всё хорошо? 🤔"));
            }
        }

        // ── 20-я транзакция — первый тип ────────────────────────
        long totalTxEver = txRepo.countByUser(user);
        if (totalTxEver == 20) {
            result.add(new TriggerMessage("pos_20th_tx",
                    "🧠 Монетка изучала тебя 20 транзакций.\n\n" +
                            "Результаты засекречены.\n" +
                            "Полный психологический портрет — 1-го числа.\n" +
                            "Готовься. Будет честно. 😄"));
        }

        // ── 7 дней подряд записывает ─────────────────────────────
        long streak = countDaysStreak(monthExpenses);
        if (streak == 7) {
            result.add(new TriggerMessage("pos_streak_7",
                    "🔥 7 дней подряд ведёшь учёт!\n\n" +
                            "Таких как ты — меньшинство.\n" +
                            "Большинство бросают на 3-й день.\n" +
                            "Ты не большинство. 💪"));
        } else if (streak == 14) {
            result.add(new TriggerMessage("pos_streak_14",
                    "🔥🔥 14 дней подряд!\n\n" +
                            "Это уже не случайность.\n" +
                            "Это характер."));
        } else if (streak == 30) {
            result.add(new TriggerMessage("pos_streak_30",
                    "👑 30 ДНЕЙ ПОДРЯД.\n\n" +
                            "Легенда.\n" +
                            "Монетка снимает шляпу."));
        }

        // ── Понедельник — начало недели ──────────────────────────
        if (lastTx.getCreatedAt().getDayOfWeek() == DayOfWeek.MONDAY && hour < 12) {
            result.add(new TriggerMessage("neg_monday_morning",
                    "☕ Понедельник. Утро. Уже трата.\n\n" +
                            "Даже выходные не помогли.\n" +
                            "Это не критика — это наблюдение."));
        }

        // ── Первая трата дня > 5000 сом ─────────────────────────
        if (todayExpenses.size() == 1 && lastTx.getAmount().compareTo(BigDecimal.valueOf(5000)) > 0) {
            result.add(new TriggerMessage("neg_first_big_day",
                    "💸 Неплохое начало дня.\n\n" +
                            String.format("%,.0f", lastTx.getAmount()) + " сом — первая трата.\n" +
                            "День только начался.\n" +
                            "Монетка наблюдает. 👀"));
        }

        // ── Несколько трат за час ────────────────────────────────
        long lastHourTx = todayExpenses.stream()
                .filter(t -> t.getCreatedAt().isAfter(now.minusHours(1))).count();
        if (lastHourTx >= 4) {
            result.add(new TriggerMessage("neg_shopping_spree",
                    "🛍️ " + lastHourTx + " покупки за последний час.\n\n" +
                            "Это называется шоппинг-терапия.\n" +
                            "Терапия дорогая.\n" +
                            "Эффективная — но дорогая."));
        }

        return result;
    }

    private long countNightDaysInRow(List<Transaction> expenses) {
        long days = 0;
        LocalDate prev = null;
        for (var t : expenses) {
            int h = t.getCreatedAt().getHour();
            if (h >= 22 || h < 6) {
                LocalDate d = t.getCreatedAt().toLocalDate();
                if (prev == null || d.equals(prev.minusDays(1))) { days++; prev = d; }
            }
        }
        return days;
    }

    private long countCategoryDaysInRow(List<Transaction> expenses, String... keywords) {
        long days = 0;
        LocalDate prev = null;
        for (var t : expenses) {
            if (t.getCategory() == null) continue;
            String catName = t.getCategory().getName().toLowerCase();
            boolean match = java.util.Arrays.stream(keywords)
                    .anyMatch(catName::contains);
            if (match) {
                LocalDate d = t.getCreatedAt().toLocalDate();
                if (prev == null || d.equals(prev.minusDays(1))) { days++; prev = d; }
            }
        }
        return days;
    }

    private long countDaysStreak(List<Transaction> expenses) {
        if (expenses.isEmpty()) return 0;
        LocalDate today = LocalDate.now(BISHKEK);
        long streak = 0;
        LocalDate check = today;
        var days = expenses.stream()
                .map(t -> t.getCreatedAt().toLocalDate())
                .collect(java.util.stream.Collectors.toSet());
        while (days.contains(check)) { streak++; check = check.minusDays(1); }
        return streak;
    }
}