package com.monetka.service;

import com.monetka.model.BudgetGoal;
import com.monetka.model.Category;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.BudgetGoalRepository;
import com.monetka.repository.CategoryRepository;
import com.monetka.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Manages budget goals (цели) per category.
 * Called from message/callback handlers after saving a transaction.
 */
@Service
public class BudgetService {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private final BudgetGoalRepository  goalRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository    categoryRepository;

    public BudgetService(BudgetGoalRepository goalRepository,
                         TransactionRepository transactionRepository,
                         CategoryRepository categoryRepository) {
        this.goalRepository        = goalRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository    = categoryRepository;
    }

    // ================================================================
    // CRUD
    // ================================================================

    @Transactional(readOnly = true)
    public List<BudgetGoal> getGoals(User user) {
        return goalRepository.findAllByUser(user);
    }

    @Transactional(readOnly = true)
    public Optional<BudgetGoal> getGoal(User user, Category category) {
        return goalRepository.findByUserAndCategory(user, category);
    }

    @Transactional(readOnly = true)
    public Optional<BudgetGoal> getGoalById(User user, Long categoryId) {
        return categoryRepository.findById(categoryId)
                .flatMap(cat -> goalRepository.findByUserAndCategory(user, cat));
    }

    @Transactional
    public BudgetGoal setGoal(User user, Category category, BigDecimal amount) {
        Optional<BudgetGoal> existing = goalRepository.findByUserAndCategory(user, category);
        if (existing.isPresent()) {
            existing.get().setAmount(amount);
            return goalRepository.save(existing.get());
        }
        return goalRepository.save(new BudgetGoal(user, category, amount));
    }

    @Transactional
    public void deleteGoal(User user, Category category) {
        goalRepository.deleteByUserAndCategory(user, category);
    }

    // ================================================================
    // Check goal after expense — returns alert message if threshold crossed
    // ================================================================

    /**
     * Call this after saving an expense.
     * Returns Optional<String> with alert message if 80% or 100% threshold crossed.
     * Returns empty if no goal set, or threshold not yet crossed.
     */
    @Transactional(readOnly = true)
    public Optional<String> checkAfterExpense(User user, Category category) {
        if (category == null) return Optional.empty();

        Optional<BudgetGoal> goalOpt = goalRepository.findByUserAndCategory(user, category);
        if (goalOpt.isEmpty()) return Optional.empty();

        BudgetGoal goal = goalOpt.get();
        BigDecimal spent = getMonthSpentForCategory(user, category);

        if (goal.getAmount().compareTo(BigDecimal.ZERO) == 0) return Optional.empty();

        int percent = spent.multiply(BigDecimal.valueOf(100))
                .divide(goal.getAmount(), 0, RoundingMode.HALF_UP)
                .intValue();

        BigDecimal remaining = goal.getAmount().subtract(spent);
        String catLabel = category.getEmoji() != null
                ? category.getEmoji() + " " + category.getName()
                : category.getName();

        // Прогресс-бар 10 делений
        int filled = Math.min(10, percent / 10);
        String bar = "█".repeat(filled) + "░".repeat(10 - filled);

        if (percent >= 100) {
            BigDecimal over = spent.subtract(goal.getAmount());
            return Optional.of(
                    "🔴 *" + catLabel + " — лимит исчерпан*\n\n" +
                            "`" + bar + "`  _" + percent + "%_\n" +
                            "💸 " + fmt(spent) + " из *" + fmt(goal.getAmount()) + "*\n" +
                            "⚠️ Превышение: *" + fmt(over) + "*\n\n" +
                            "_Записываю дальше — просто имей в виду 📝_"
            );
        } else if (percent >= 90) {
            return Optional.of(
                    "🟡 *" + catLabel + "* — почти лимит\n\n" +
                            "`" + bar + "`  _" + percent + "%_\n" +
                            "💸 " + fmt(spent) + " из *" + fmt(goal.getAmount()) + "*\n" +
                            "✅ Осталось: *" + fmt(remaining) + "*\n\n" +
                            "_Держись, финишная прямая 😉_"
            );
        } else if (percent >= 80) {
            return Optional.of(
                    "🟡 *" + catLabel + "* — 80% лимита\n\n" +
                            "`" + bar + "`  _" + percent + "%_\n" +
                            "💸 " + fmt(spent) + " из *" + fmt(goal.getAmount()) + "*\n" +
                            "✅ Осталось: *" + fmt(remaining) + "*\n\n" +
                            "_Всё под контролем 👌_"
            );
        }

        return Optional.empty();
    }

    // ================================================================
    // Suggest goal based on 3-month average
    // ================================================================

    @Transactional(readOnly = true)
    public BigDecimal suggestGoal(User user, Category category) {
        LocalDate now = LocalDate.now(BISHKEK);
        BigDecimal total = BigDecimal.ZERO;
        int months = 0;

        for (int i = 1; i <= 3; i++) {
            LocalDate monthStart = now.minusMonths(i).withDayOfMonth(1);
            LocalDateTime from = monthStart.atStartOfDay();
            LocalDateTime to   = monthStart.plusMonths(1).atStartOfDay();

            BigDecimal spent = transactionRepository
                    .sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);
            // filter by category — use raw query result
            BigDecimal catSpent = getCategorySpentForPeriod(user, category, from, to);
            if (catSpent.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(catSpent);
                months++;
            }
        }

        if (months == 0) return BigDecimal.ZERO;

        // average + 10% buffer, rounded to nearest 500
        BigDecimal avg = total.divide(BigDecimal.valueOf(months), 0, RoundingMode.HALF_UP);
        BigDecimal suggested = avg.multiply(BigDecimal.valueOf(1.1))
                .divide(BigDecimal.valueOf(500), 0, RoundingMode.CEILING)
                .multiply(BigDecimal.valueOf(500));
        return suggested;
    }

    // ================================================================
    // Month spent per category
    // ================================================================

    @Transactional(readOnly = true)
    public BigDecimal getMonthSpentForCategory(User user, Category category) {
        LocalDate now = LocalDate.now(BISHKEK);
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);
        return getCategorySpentForPeriod(user, category, from, to);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCategorySpentForPeriod(User user, Category category,
                                                LocalDateTime from, LocalDateTime to) {
        BigDecimal result = transactionRepository
                .sumByUserCategoryAndPeriod(user, category, from, to);
        return result != null ? result : BigDecimal.ZERO;
    }

    private String fmt(BigDecimal v) { return String.format("%,.0f сом", v); }
}