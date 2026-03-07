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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Manages payday cycles — tracks spending pace between income events.
 *
 * Rules:
 *  - Every income (salary or other) creates or updates the active cycle.
 *  - Cycle has no fixed end date — it runs until next income.
 *  - After each expense, caller can ask for a pace hint via getPaceHint().
 *  - No separate accounts, no user choices — everything is automatic.
 */
@Service
public class PaydayService {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private final PaydayCycleRepository  cycleRepository;
    private final TransactionRepository  transactionRepository;

    public PaydayService(PaydayCycleRepository cycleRepository,
                         TransactionRepository transactionRepository) {
        this.cycleRepository     = cycleRepository;
        this.transactionRepository = transactionRepository;
    }

    // ================================================================
    // Called after EVERY income — creates new cycle or adds to current
    // ================================================================

    @Transactional
    public void onIncome(User user, BigDecimal amount) {
        Optional<PaydayCycle> existing = cycleRepository.findByUserAndActiveTrue(user);

        if (existing.isPresent()) {
            // Add income to current cycle (handles mid-cycle freelance etc.)
            PaydayCycle cycle = existing.get();
            cycle.setTotalIncome(cycle.getTotalIncome().add(amount));
            cycleRepository.save(cycle);
        } else {
            // Start fresh cycle
            cycleRepository.save(new PaydayCycle(user, amount));
        }
    }

    // ================================================================
    // Called after every EXPENSE — returns optional hint line
    // Returns empty if no active cycle or not enough data
    // ================================================================

    @Transactional(readOnly = true)
    public Optional<String> getPaceHint(User user) {
        Optional<PaydayCycle> cycleOpt = cycleRepository.findByUserAndActiveTrue(user);
        if (cycleOpt.isEmpty()) return Optional.empty();

        PaydayCycle cycle = cycleOpt.get();
        LocalDate today     = LocalDate.now(BISHKEK);
        LocalDate startDate = cycle.getStartDate();

        long daysPassed = ChronoUnit.DAYS.between(startDate, today) + 1;

        // Calculate spent since cycle start
        LocalDateTime cycleFrom = startDate.atStartOfDay();
        LocalDateTime cycleTo   = today.plusDays(1).atStartOfDay();
        BigDecimal spent = safe(transactionRepository.sumByUserAndTypeAndPeriod(
                user, TransactionType.EXPENSE, cycleFrom, cycleTo));

        BigDecimal totalIncome = cycle.getTotalIncome();
        BigDecimal remaining   = totalIncome.subtract(spent);

        if (totalIncome.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();

        // Daily budget = total / assumed 30 days (or days passed if > 30)
        long totalDays = Math.max(30, daysPassed);
        BigDecimal dailyBudget = totalIncome
                .divide(BigDecimal.valueOf(totalDays), 0, RoundingMode.HALF_UP);

        // Actual daily spend rate
        BigDecimal actualDaily = daysPassed > 0
                ? spent.divide(BigDecimal.valueOf(daysPassed), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Days remaining at current pace
        long daysLeft = remaining.compareTo(BigDecimal.ZERO) > 0 && actualDaily.compareTo(BigDecimal.ZERO) > 0
                ? remaining.divide(actualDaily, 0, RoundingMode.DOWN).longValue()
                : 0;

        // Build hint
        int spentPct = spent.multiply(BigDecimal.valueOf(100))
                .divide(totalIncome, 0, RoundingMode.HALF_UP).intValue();

        if (actualDaily.compareTo(dailyBudget) <= 0) {
            // On track
            return Optional.of(
                    "\uD83D\uDCC5 \u0414\u0435\u043d\u044c " + daysPassed + " \u0446\u0438\u043a\u043b\u0430  \u2022  " +
                            "\u0442\u0440\u0430\u0447\u0443 " + fmt(actualDaily) + "/\u0434\u0435\u043d\u044c \u2705"
            );
        } else if (spentPct >= 90) {
            // Critical
            return Optional.of(
                    "\uD83D\uDD34 \u041f\u043e\u0442\u0440\u0430\u0447\u0435\u043d\u043e " + spentPct + "% \u0431\u044e\u0434\u0436\u0435\u0442\u0430  \u2022  " +
                            "\u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c *" + fmt(remaining) + "*"
            );
        } else {
            // Warning
            return Optional.of(
                    "\uD83D\uDFE1 " + fmt(actualDaily) + "/\u0434\u0435\u043d\u044c" +
                            " (\u043f\u043b\u0430\u043d " + fmt(dailyBudget) + ")  \u2022  " +
                            "\u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c *" + fmt(remaining) + "*"
            );
        }
    }

    // ================================================================
    // Full cycle status — for /день command or overview
    // ================================================================

    @Transactional(readOnly = true)
    public Optional<CycleStatus> getCycleStatus(User user) {
        Optional<PaydayCycle> cycleOpt = cycleRepository.findByUserAndActiveTrue(user);
        if (cycleOpt.isEmpty()) return Optional.empty();

        PaydayCycle cycle = cycleOpt.get();
        LocalDate today     = LocalDate.now(BISHKEK);
        LocalDate startDate = cycle.getStartDate();
        long daysPassed = ChronoUnit.DAYS.between(startDate, today) + 1;

        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to   = today.plusDays(1).atStartOfDay();
        BigDecimal spent = safe(transactionRepository.sumByUserAndTypeAndPeriod(
                user, TransactionType.EXPENSE, from, to));

        BigDecimal totalIncome = cycle.getTotalIncome();
        BigDecimal remaining   = totalIncome.subtract(spent);

        long totalDays = Math.max(30, daysPassed);
        BigDecimal dailyBudget = totalIncome
                .divide(BigDecimal.valueOf(totalDays), 0, RoundingMode.HALF_UP);
        BigDecimal actualDaily = daysPassed > 0
                ? spent.divide(BigDecimal.valueOf(daysPassed), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return Optional.of(new CycleStatus(
                totalIncome, spent, remaining,
                dailyBudget, actualDaily,
                daysPassed, startDate
        ));
    }

    // ================================================================
    // Inner class — cycle status DTO
    // ================================================================

    public static class CycleStatus {
        public final BigDecimal totalIncome;
        public final BigDecimal spent;
        public final BigDecimal remaining;
        public final BigDecimal dailyBudget;
        public final BigDecimal actualDaily;
        public final long daysPassed;
        public final LocalDate startDate;

        public CycleStatus(BigDecimal totalIncome, BigDecimal spent,
                           BigDecimal remaining, BigDecimal dailyBudget,
                           BigDecimal actualDaily, long daysPassed,
                           LocalDate startDate) {
            this.totalIncome  = totalIncome;
            this.spent        = spent;
            this.remaining    = remaining;
            this.dailyBudget  = dailyBudget;
            this.actualDaily  = actualDaily;
            this.daysPassed   = daysPassed;
            this.startDate    = startDate;
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private String fmt(BigDecimal v)      { return String.format("%,.0f \u0441\u043e\u043c", v); }
}