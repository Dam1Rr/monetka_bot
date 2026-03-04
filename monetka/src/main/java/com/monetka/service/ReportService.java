package com.monetka.service;

import com.monetka.model.Subscription;
import com.monetka.model.Transaction;
import com.monetka.model.User;
import com.monetka.model.enums.TransactionType;
import com.monetka.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final StatisticsService     statisticsService;
    private final SubscriptionService   subscriptionService;
    private final TransactionRepository transactionRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ---- Daily report (sent at 21:00) ----

    @Transactional(readOnly = true)
    public String buildDailyReport(User user) {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to   = from.plusDays(1);

        BigDecimal totalExpense = transactionRepository
            .sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);
        BigDecimal totalIncome = transactionRepository
            .sumByUserAndTypeAndPeriod(user, TransactionType.INCOME, from, to);

        Map<String, BigDecimal> byCategory =
            statisticsService.getExpensesByCategory(user, from, to);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Отчёт за ").append(LocalDate.now().format(DATE_FMT)).append("*\n\n");

        if (totalExpense.compareTo(BigDecimal.ZERO) == 0
                && totalIncome.compareTo(BigDecimal.ZERO) == 0) {
            sb.append("Сегодня транзакций не было 🌙");
            return sb.toString();
        }

        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("💰 Доход: *+").append(fmt(totalIncome)).append("*\n");
        }

        if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("💸 Расход: *-").append(fmt(totalExpense)).append("*\n\n");
            sb.append("По категориям:\n");
            byCategory.forEach((cat, amount) ->
                sb.append("  ").append(cat).append(" — ")
                  .append(fmt(amount)).append("\n")
            );
        }

        sb.append("\n💳 Баланс: *").append(fmt(user.getBalance())).append("*");
        return sb.toString();
    }

    // ---- Month statistics ----

    @Transactional(readOnly = true)
    public String buildMonthStats(User user) {
        LocalDateTime from = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        BigDecimal income   = statisticsService.getMonthIncome(user);
        BigDecimal expenses = statisticsService.getMonthExpenses(user);
        BigDecimal diff     = income.subtract(expenses);

        Map<String, BigDecimal> byCategory =
            statisticsService.getExpensesByCategory(user, from, to);

        String month = statisticsService.currentMonthName();

        StringBuilder sb = new StringBuilder();
        sb.append("📈 *Статистика за ").append(month).append("*\n\n");
        sb.append("💰 Доходы: *+").append(fmt(income)).append("*\n");
        sb.append("💸 Расходы: *-").append(fmt(expenses)).append("*\n");
        sb.append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "✅" : "⚠️");
        sb.append(" Итого: *").append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
          .append(fmt(diff)).append("*\n");

        if (!byCategory.isEmpty()) {
            sb.append("\n*Расходы по категориям:*\n");
            byCategory.forEach((cat, amount) ->
                sb.append("  ").append(cat).append(" — ")
                  .append(fmt(amount)).append("\n")
            );
        }

        return sb.toString();
    }

    // ---- Subscriptions list ----

    @Transactional(readOnly = true)
    public String buildSubscriptionsList(User user) {
        List<Subscription> subs = subscriptionService.getActiveSubscriptions(user);

        if (subs.isEmpty()) return "У вас нет активных подписок.";

        StringBuilder sb = new StringBuilder("🔄 *Ваши подписки:*\n\n");
        BigDecimal total = BigDecimal.ZERO;

        for (Subscription sub : subs) {
            sb.append("• ").append(sub.getName())
              .append(" — *").append(fmt(sub.getAmount())).append("*/мес")
              .append(" (").append(sub.getDayOfMonth()).append(" числа)")
              .append("\n");
            total = total.add(sub.getAmount());
        }

        sb.append("\nИтого в месяц: *").append(fmt(total)).append("*");
        return sb.toString();
    }

    // ---- Util ----

    private String fmt(BigDecimal amount) {
        return String.format("%,.0f ₸", amount);
    }
}
