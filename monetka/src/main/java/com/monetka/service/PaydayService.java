package com.monetka.service;

import com.monetka.model.PaydayCycle;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.PaydayCycleRepository;
import com.monetka.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class PaydayService {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private final PaydayCycleRepository cycleRepository;
    private final TransactionRepository transactionRepository;

    public PaydayService(PaydayCycleRepository cycleRepository,
                         TransactionRepository transactionRepository) {
        this.cycleRepository      = cycleRepository;
        this.transactionRepository = transactionRepository;
    }

    // ================================================================
    // Called after EVERY income — creates new cycle or updates current
    // ================================================================

    @Transactional
    public void onIncome(User user, BigDecimal amount) {
        Optional<PaydayCycle> existing = cycleRepository.findByUserAndActiveTrue(user);
        if (existing.isPresent()) {
            PaydayCycle cycle = existing.get();
            cycle.setTotalIncome(cycle.getTotalIncome().add(amount));
            cycleRepository.save(cycle);
        } else {
            cycleRepository.save(new PaydayCycle(user, amount));
        }
    }

    // ================================================================
    // Pace hint — one line after each expense
    // ================================================================

    @Transactional(readOnly = true)
    public Optional<String> getPaceHint(User user) {
        return getCycleStatus(user).map(s -> {
            if (s.daysLeft <= 0) return null;
            int cmp = s.actualDaily.compareTo(s.dailyBudget);
            if (cmp <= 0) {
                return "\uD83D\uDCC5 \u0414\u0435\u043d\u044c " + s.daysPassed +
                        "  \u2022  \u0442\u0440\u0430\u0447\u0443 " + fmt(s.actualDaily) + "/\u0434\u0435\u043d\u044c \u2705";
            } else {
                return "\uD83D\uDFE1 \u0422\u0440\u0430\u0447\u0443 " + fmt(s.actualDaily) +
                        "/\u0434\u0435\u043d\u044c  \u2022  \u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c *" +
                        fmt(s.remaining) + "*";
            }
        }).filter(h -> h != null);
    }

    // ================================================================
    // Full cycle status
    // ================================================================

    @Transactional(readOnly = true)
    public Optional<CycleStatus> getCycleStatus(User user) {
        Optional<PaydayCycle> cycleOpt = cycleRepository.findByUserAndActiveTrue(user);
        if (cycleOpt.isEmpty()) return Optional.empty();

        PaydayCycle cycle  = cycleOpt.get();
        LocalDate today    = LocalDate.now(BISHKEK);
        LocalDate start    = cycle.getStartDate();

        // Days passed since cycle start (minimum 1)
        long daysPassed = Math.max(1, ChronoUnit.DAYS.between(start, today) + 1);

        // Days left until end of current month
        LocalDate endOfMonth = YearMonth.from(today).atEndOfMonth();
        long daysLeft = ChronoUnit.DAYS.between(today, endOfMonth); // 0 on last day

        // Total cycle days = days passed + days left
        long totalCycleDays = daysPassed + daysLeft;

        // Spent since cycle start
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to   = today.plusDays(1).atStartOfDay();
        BigDecimal spent   = safe(transactionRepository.sumByUserAndTypeAndPeriod(
                user, TransactionType.EXPENSE, from, to));

        BigDecimal totalIncome = cycle.getTotalIncome();
        BigDecimal remaining   = totalIncome.subtract(spent);

        // Daily budget based on REAL remaining days in month
        // remaining money / remaining days (min 1 to avoid division by zero)
        long budgetDays = Math.max(1, daysLeft + 1); // +1 to include today
        BigDecimal dailyBudget = remaining.compareTo(BigDecimal.ZERO) > 0
                ? remaining.divide(BigDecimal.valueOf(budgetDays), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Actual daily spend rate
        BigDecimal actualDaily = spent.divide(BigDecimal.valueOf(daysPassed), 0, RoundingMode.HALF_UP);

        // Forecast: how much will be spent by end of month at current pace
        BigDecimal forecast = actualDaily.multiply(BigDecimal.valueOf(totalCycleDays));

        return Optional.of(new CycleStatus(
                totalIncome, spent, remaining,
                dailyBudget, actualDaily, forecast,
                daysPassed, daysLeft, totalCycleDays,
                start, endOfMonth
        ));
    }

    // ================================================================
    // Smart analysis text for Overview screen
    // ================================================================

    @Transactional(readOnly = true)
    public Optional<String> getSmartAnalysis(User user) {
        return getCycleStatus(user).map(s -> {
            StringBuilder sb = new StringBuilder();
            sb.append("\n\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
            sb.append("\uD83D\uDCB0 *\u0414\u043e \u043a\u043e\u043d\u0446\u0430 \u043c\u0435\u0441\u044f\u0446\u0430*\n\n");

            // Days info
            sb.append("\uD83D\uDCC5 \u041e\u0441\u0442\u0430\u043b\u043e\u0441\u044c \u0434\u043d\u0435\u0439: *").append(s.daysLeft).append("*\n");
            sb.append("\uD83D\uDCB5 \u041e\u0441\u0442\u0430\u0442\u043e\u043a: *").append(fmt(s.remaining)).append("*\n");
            sb.append("\uD83D\uDCCA \u041c\u043e\u0436\u043d\u043e \u0442\u0440\u0430\u0442\u0438\u0442\u044c: *").append(fmt(s.dailyBudget)).append("/\u0434\u0435\u043d\u044c*\n");

            // Pace analysis
            sb.append("\n");
            int pct = s.totalIncome.compareTo(BigDecimal.ZERO) > 0
                    ? s.spent.multiply(BigDecimal.valueOf(100))
                    .divide(s.totalIncome, 0, RoundingMode.HALF_UP).intValue()
                    : 0;

            int cmp = s.actualDaily.compareTo(s.dailyBudget);
            if (cmp <= 0) {
                // On track
                BigDecimal willSave = s.totalIncome.subtract(s.forecast);
                sb.append("\u2705 *\u041e\u0442\u043b\u0438\u0447\u043d\u044b\u0439 \u0442\u0435\u043c\u043f!*\n");
                if (willSave.compareTo(BigDecimal.ZERO) > 0) {
                    sb.append("\uD83C\uDFAF \u0421\u0431\u0435\u0440\u0435\u0436\u0451\u0448\u044c *")
                            .append(fmt(willSave)).append("* \u043a \u043a\u043e\u043d\u0446\u0443 \u043c\u0435\u0441\u044f\u0446\u0430\n");
                }
            } else {
                // Over budget — simple human language
                BigDecimal deficit = s.forecast.subtract(s.totalIncome);
                sb.append("\uD83D\uDFE1 *\u0422\u0440\u0430\u0442\u0438\u0448\u044c \u0447\u0443\u0442\u044c \u0431\u043e\u043b\u044c\u0448\u0435 \u043f\u043b\u0430\u043d\u0430*\n");
                sb.append("\uD83D\uDCCA \u0421\u0435\u0439\u0447\u0430\u0441: *").append(fmt(s.actualDaily))
                        .append("/\u0434\u0435\u043d\u044c*,  \u043f\u043b\u0430\u043d: *").append(fmt(s.dailyBudget)).append("/\u0434\u0435\u043d\u044c*\n");
                if (deficit.compareTo(BigDecimal.ZERO) > 0) {
                    sb.append("\u26A0\uFE0F \u0415\u0441\u043b\u0438 \u0442\u0430\u043a \u043f\u0440\u043e\u0434\u043e\u043b\u0436\u0438\u0448\u044c — \u0432 \u043a\u043e\u043d\u0446\u0435 \u043c\u0435\u0441\u044f\u0446\u0430 \u043d\u0435 \u0445\u0432\u0430\u0442\u0438\u0442 *")
                            .append(fmt(deficit)).append("*\n");
                }
            }

            // Forecast line — show what will be SPENT, not the deficit
            sb.append("\n\uD83D\uDD2E \u041a \u043a\u043e\u043d\u0446\u0443 \u043c\u0435\u0441\u044f\u0446\u0430 \u043f\u043e\u0442\u0440\u0430\u0442\u0438\u0448\u044c \u043f\u0440\u0438\u043c\u0435\u0440\u043d\u043e *");
            sb.append(fmt(s.forecast)).append("*");

            return sb.toString();
        });
    }

    // ================================================================
    // DTO
    // ================================================================

    public static class CycleStatus {
        public final BigDecimal totalIncome;
        public final BigDecimal spent;
        public final BigDecimal remaining;
        public final BigDecimal dailyBudget;
        public final BigDecimal actualDaily;
        public final BigDecimal forecast;
        public final long       daysPassed;
        public final long       daysLeft;
        public final long       totalCycleDays;
        public final LocalDate  startDate;
        public final LocalDate  endOfMonth;

        public CycleStatus(BigDecimal totalIncome, BigDecimal spent,
                           BigDecimal remaining, BigDecimal dailyBudget,
                           BigDecimal actualDaily, BigDecimal forecast,
                           long daysPassed, long daysLeft, long totalCycleDays,
                           LocalDate startDate, LocalDate endOfMonth) {
            this.totalIncome    = totalIncome;
            this.spent          = spent;
            this.remaining      = remaining;
            this.dailyBudget    = dailyBudget;
            this.actualDaily    = actualDaily;
            this.forecast       = forecast;
            this.daysPassed     = daysPassed;
            this.daysLeft       = daysLeft;
            this.totalCycleDays = totalCycleDays;
            this.startDate      = startDate;
            this.endOfMonth     = endOfMonth;
        }
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private String fmt(BigDecimal v)      { return String.format("%,.0f \u0441\u043e\u043c", v); }
}