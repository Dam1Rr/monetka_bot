package com.monetka.service;

import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final TransactionRepository transactionRepository;

    /**
     * Returns expense breakdown by category for the given period.
     * key   = "emoji name"  (e.g. "🍕 Еда")
     * value = total amount
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getExpensesByCategory(User user,
                                                          LocalDateTime from,
                                                          LocalDateTime to) {
        List<Object[]> rows = transactionRepository
            .sumExpensesByCategoryAndPeriod(user, from, to);

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String name    = (String)     row[0];
            String emoji   = (String)     row[1];
            BigDecimal sum = (BigDecimal) row[2];

            String label = (emoji != null ? emoji + " " : "") + name;
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

    // ---- Helpers ----

    private BigDecimal sumForCurrentMonth(User user, TransactionType type) {
        LocalDateTime from = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);
        return transactionRepository.sumByUserAndTypeAndPeriod(user, type, from, to);
    }

    public String currentMonthName() {
        return LocalDate.now().getMonth()
            .getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));
    }
}
