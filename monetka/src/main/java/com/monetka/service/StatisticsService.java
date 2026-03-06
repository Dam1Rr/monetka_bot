package com.monetka.service;

import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;

@Service
public class StatisticsService {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private final TransactionRepository transactionRepository;

    public StatisticsService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    // ================================================================
    // CategoryStats — иерархия: категория → подкатегории с суммами
    // ================================================================

    public static class SubcategoryAmount {
        public final String     name;
        public final BigDecimal amount;

        SubcategoryAmount(String name, BigDecimal amount) {
            this.name   = name;
            this.amount = amount;
        }
    }

    public static class CategoryStats {
        public final String                  label;      // "🍕 Еда"
        public final BigDecimal              total;
        public final int                     percent;    // % от всех расходов
        public final List<SubcategoryAmount> subcats;    // убывающий порядок

        CategoryStats(String label, BigDecimal total, int percent,
                      List<SubcategoryAmount> subcats) {
            this.label   = label;
            this.total   = total;
            this.percent = percent;
            this.subcats = subcats;
        }
    }

    /**
     * Returns expense breakdown by category WITH subcategories and percentages.
     * Sorted by total DESC.
     */
    @Transactional(readOnly = true)
    public List<CategoryStats> getDetailedExpenses(User user,
                                                   LocalDateTime from,
                                                   LocalDateTime to) {
        // Row: [catName, catEmoji, subcatName(nullable), amount]
        List<Object[]> rows = transactionRepository.sumExpensesByCategoryAndSubcategoryAndPeriod(user, from, to);

        // Group into category → (subcatName → sum)
        LinkedHashMap<String, BigDecimal> catTotals    = new LinkedHashMap<>();
        LinkedHashMap<String, String>     catEmojis    = new LinkedHashMap<>();
        LinkedHashMap<String, LinkedHashMap<String, BigDecimal>> catSubcats = new LinkedHashMap<>();

        for (Object[] row : rows) {
            String catName    = (String)     row[0];
            String catEmoji   = (String)     row[1];
            String subcatName = (String)     row[2]; // nullable
            BigDecimal amount = (BigDecimal) row[3];

            catEmojis.putIfAbsent(catName, catEmoji != null ? catEmoji : "");
            catTotals.merge(catName, amount, BigDecimal::add);

            catSubcats.computeIfAbsent(catName, k -> new LinkedHashMap<>())
                    .merge(subcatName != null ? subcatName : "_без_подкатегории_", amount, BigDecimal::add);
        }

        // Total for percentage calculation
        BigDecimal grandTotal = catTotals.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build CategoryStats list, sorted by total DESC
        List<CategoryStats> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : catTotals.entrySet()) {
            String catName  = e.getKey();
            BigDecimal total = e.getValue();
            String emoji    = catEmojis.getOrDefault(catName, "");
            String label    = (emoji.isBlank() ? "" : emoji + " ") + catName;

            int percent = 0;
            if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                percent = total.multiply(BigDecimal.valueOf(100))
                        .divide(grandTotal, 0, RoundingMode.HALF_UP)
                        .intValue();
            }

            // Subcategories — sorted by amount DESC, skip the "_без_подкатегории_" sentinel
            List<SubcategoryAmount> subcats = new ArrayList<>();
            LinkedHashMap<String, BigDecimal> subMap = catSubcats.getOrDefault(catName, new LinkedHashMap<>());
            subMap.entrySet().stream()
                    .filter(se -> !se.getKey().equals("_без_подкатегории_"))
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(se -> subcats.add(new SubcategoryAmount(se.getKey(), se.getValue())));

            result.add(new CategoryStats(label, total, percent, subcats));
        }

        result.sort((a, b) -> b.total.compareTo(a.total));
        return result;
    }

    /**
     * Simple map for backward compat (daily report totals).
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getExpensesByCategory(User user,
                                                         LocalDateTime from,
                                                         LocalDateTime to) {
        List<Object[]> rows = transactionRepository.sumExpensesByCategoryAndPeriod(user, from, to);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String name    = (String)     row[0];
            String emoji   = (String)     row[1];
            BigDecimal sum = (BigDecimal) row[2];
            String label   = (emoji != null ? emoji + " " : "") + name;
            result.put(label, sum);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthIncome(User user) {
        return sumForCurrentMonth(user, TransactionType.INCOME);
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthExpenses(User user) {
        return sumForCurrentMonth(user, TransactionType.EXPENSE);
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthIncomeForPeriod(User user, LocalDateTime from, LocalDateTime to) {
        BigDecimal r = transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.INCOME, from, to);
        return r != null ? r : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthExpensesForPeriod(User user, LocalDateTime from, LocalDateTime to) {
        BigDecimal r = transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);
        return r != null ? r : BigDecimal.ZERO;
    }

    private BigDecimal sumForCurrentMonth(User user, TransactionType type) {
        LocalDate now  = LocalDate.now(BISHKEK);
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);
        BigDecimal result  = transactionRepository.sumByUserAndTypeAndPeriod(user, type, from, to);
        return result != null ? result : BigDecimal.ZERO;
    }

    public String currentMonthName() {
        return LocalDate.now(BISHKEK).getMonth()
                .getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));
    }
}