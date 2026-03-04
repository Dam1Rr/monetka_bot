package com.monetka.service;

import com.monetka.model.Subscription;
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
    private final FinancialTipsService  tipsService;
    private final TransactionRepository transactionRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ================================================================
    // Ежедневный отчёт (21:00)
    // ================================================================

    @Transactional(readOnly = true)
    public String buildDailyReport(User user) {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to   = from.plusDays(1);

        BigDecimal expense = transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);
        BigDecimal income  = transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.INCOME, from, to);
        Map<String, BigDecimal> byCategory = statisticsService.getExpensesByCategory(user, from, to);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Итоги дня — ").append(LocalDate.now().format(DATE_FMT)).append("*\n\n");

        if (expense.compareTo(BigDecimal.ZERO) == 0 && income.compareTo(BigDecimal.ZERO) == 0) {
            sb.append("Сегодня транзакций не было 🌙\n\n");
            sb.append(tipsService.randomTip());
            return sb.toString();
        }

        if (income.compareTo(BigDecimal.ZERO) > 0)
            sb.append("💰 Доход: *+").append(fmt(income)).append("*\n");

        if (expense.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("💸 Расход: *-").append(fmt(expense)).append("*\n\n");
            sb.append("По категориям:\n");
            byCategory.forEach((cat, amt) ->
                    sb.append("  ").append(cat).append(" — ").append(fmt(amt)).append("\n"));
        }

        sb.append("\n💳 Баланс: *").append(fmt(user.getBalance())).append("*");
        sb.append("\n\n").append(tipsService.randomTip());
        return sb.toString();
    }

    // ================================================================
    // Статистика — сегодня
    // ================================================================

    @Transactional(readOnly = true)
    public String buildTodayStats(User user) {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to   = from.plusDays(1);
        return buildPeriodStats(user, from, to, "сегодня");
    }

    // ================================================================
    // Статистика — неделя
    // ================================================================

    @Transactional(readOnly = true)
    public String buildWeekStats(User user) {
        LocalDateTime from = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime to   = LocalDate.now().plusDays(1).atStartOfDay();
        return buildPeriodStats(user, from, to, "последние 7 дней");
    }

    // ================================================================
    // Статистика — месяц
    // ================================================================

    @Transactional(readOnly = true)
    public String buildMonthStats(User user) {
        LocalDateTime from = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        BigDecimal income   = statisticsService.getMonthIncome(user);
        BigDecimal expenses = statisticsService.getMonthExpenses(user);
        BigDecimal diff     = income.subtract(expenses);
        Map<String, BigDecimal> byCategory = statisticsService.getExpensesByCategory(user, from, to);
        String month = statisticsService.currentMonthName();

        StringBuilder sb = new StringBuilder();
        sb.append("📈 *Статистика за ").append(month).append("*\n\n");
        sb.append("💰 Доходы: *+").append(fmt(income)).append("*\n");
        sb.append("💸 Расходы: *-").append(fmt(expenses)).append("*\n");
        sb.append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "✅" : "⚠️");
        sb.append(" Итого: *").append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "").append(fmt(diff)).append("*\n");

        if (!byCategory.isEmpty()) {
            sb.append("\n*По категориям:*\n");
            byCategory.forEach((cat, amt) ->
                    sb.append("  ").append(cat).append(" — ").append(fmt(amt)).append("\n"));
        }

        return sb.toString();
    }

    // ================================================================
    // Список подписок с таймером и советами
    // ================================================================

    @Transactional(readOnly = true)
    public String buildSubscriptionsList(User user) {
        List<Subscription> subs = subscriptionService.getActiveSubscriptions(user);

        if (subs.isEmpty()) return "У вас нет активных подписок.\n\nДобавь первую — нажми кнопку ниже 👇";

        StringBuilder sb = new StringBuilder("🔄 *Твои подписки:*\n\n");
        BigDecimal totalMonth = BigDecimal.ZERO;
        BigDecimal totalYear  = BigDecimal.ZERO;

        for (Subscription sub : subs) {
            sb.append("▸ *").append(sub.getName()).append("*\n");
            sb.append("  💸 ").append(fmt(sub.getAmount())).append("/мес\n");
            sb.append("  📅 С ").append(sub.getStartDate().format(DATE_FMT));

            if (sub.getEndDate() != null) {
                sb.append(" до ").append(sub.getEndDate().format(DATE_FMT));
                Long days = sub.daysUntilExpiry();
                if (days != null) {
                    if (days == 0)       sb.append(" ⚠️ *истекает сегодня!*");
                    else if (days <= 3)  sb.append(" ⚠️ *осталось ").append(days).append(" дн.*");
                    else if (days <= 7)  sb.append(" ⏰ осталось ").append(days).append(" дн.");
                    else                 sb.append(" ✅ ещё ").append(days).append(" дн.");
                } else {
                    sb.append(" 🔴 *истекла*");
                }
            } else {
                sb.append(" — бессрочная ♾");
            }

            sb.append("\n\n");
            totalMonth = totalMonth.add(sub.getAmount());
            totalYear  = totalYear.add(sub.getAmount().multiply(BigDecimal.valueOf(12)));
        }

        sb.append("─────────────────\n");
        sb.append("📊 В месяц: *").append(fmt(totalMonth)).append("*\n");
        sb.append("📊 В год: *").append(fmt(totalYear)).append("*\n");
        sb.append(whatCanYouBuyForYear(totalYear));

        return sb.toString();
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String buildPeriodStats(User user, LocalDateTime from, LocalDateTime to, String label) {
        BigDecimal expense = transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.EXPENSE, from, to);
        BigDecimal income  = transactionRepository.sumByUserAndTypeAndPeriod(user, TransactionType.INCOME, from, to);
        Map<String, BigDecimal> byCategory = statisticsService.getExpensesByCategory(user, from, to);

        StringBuilder sb = new StringBuilder("📊 *Статистика за ").append(label).append("*\n\n");

        if (expense.compareTo(BigDecimal.ZERO) == 0 && income.compareTo(BigDecimal.ZERO) == 0) {
            sb.append("Транзакций нет 🌙");
            return sb.toString();
        }

        if (income.compareTo(BigDecimal.ZERO) > 0)
            sb.append("💰 Доходы: *+").append(fmt(income)).append("*\n");
        if (expense.compareTo(BigDecimal.ZERO) > 0)
            sb.append("💸 Расходы: *-").append(fmt(expense)).append("*\n");

        if (!byCategory.isEmpty()) {
            sb.append("\n*По категориям:*\n");
            byCategory.forEach((cat, amt) ->
                    sb.append("  ").append(cat).append(" — ").append(fmt(amt)).append("\n"));
        }

        return sb.toString();
    }

    private String whatCanYouBuyForYear(BigDecimal amount) {
        long sum = amount.longValue();
        if (sum < 5_000)   return "\n💡 Это немного — отличная мотивация оптимизировать подписки!";
        if (sum < 20_000)  return "\n💡 На эти деньги можно купить хорошие наушники или обновить гаджет.";
        if (sum < 50_000)  return "\n💡 Это смартфон среднего класса — подумай, нужны ли все подписки?";
        if (sum < 100_000) return "\n💡 Это ноутбук! Возможно, стоит пересмотреть список подписок.";
        return "\n⚠️ Серьёзная сумма в год — рекомендуем пересмотреть подписки!";
    }

    private String fmt(BigDecimal amount) {
        return String.format("%,.0f сом", amount);
    }
}