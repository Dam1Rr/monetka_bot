package com.monetka.insight;

import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Component
public class ScoreCalculator {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
    private final TransactionRepository txRepo;

    public ScoreCalculator(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    public Scores calculate(User user, int month, int year) {
        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        var expenses = txRepo.findByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);
        if (expenses.isEmpty()) return new Scores(0, 50, 0, 0);

        // Night %
        long nightCount = expenses.stream()
                .filter(t -> { int h = t.getCreatedAt().getHour(); return h >= 22 || h < 6; })
                .count();
        int nightPct = (int)(nightCount * 100 / expenses.size());

        // Impulse score
        BigDecimal total = expenses.stream().map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = total.divide(BigDecimal.valueOf(expenses.size()), 2, RoundingMode.HALF_UP);
        long bigTx = expenses.stream()
                .filter(t -> t.getAmount().compareTo(avg.multiply(BigDecimal.valueOf(3))) > 0)
                .count();
        int impulseScore = Math.min(100, (int)(bigTx * 20 + nightPct / 2));

        // Discipline score
        long daysWithTx = expenses.stream()
                .map(t -> t.getCreatedAt().toLocalDate()).distinct().count();
        long totalDays = Math.max(1, ChronoUnit.DAYS.between(
                from.toLocalDate(), LocalDate.now(BISHKEK)));
        int disciplineScore = Math.min(100, (int)(daysWithTx * 100 / totalDays));

        // Savings %
        BigDecimal income = txRepo.sumByUserAndTypeAndPeriod(user, TransactionType.INCOME, from, to);
        int savingsPct = 0;
        if (income != null && income.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal spent = txRepo.sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);
            BigDecimal saved = income.subtract(spent == null ? BigDecimal.ZERO : spent);
            savingsPct = Math.max(0, saved.multiply(BigDecimal.valueOf(100))
                    .divide(income, 0, RoundingMode.HALF_UP).intValue());
        }

        return new Scores(impulseScore, disciplineScore, nightPct, savingsPct);
    }

    public record Scores(int impulse, int discipline, int nightPct, int savingsPct) {}
}