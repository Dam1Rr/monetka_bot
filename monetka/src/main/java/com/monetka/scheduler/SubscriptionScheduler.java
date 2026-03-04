package com.monetka.scheduler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.Subscription;
import com.monetka.service.SubscriptionService;
import com.monetka.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Automatically creates expense transactions for active subscriptions
 * on their due day at 09:00.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final SubscriptionService subscriptionService;
    private final TransactionService  transactionService;
    private final MonetkaBot          bot;

    @Scheduled(cron = "0 0 9 * * *")
    public void chargeSubscriptions() {
        int today = LocalDate.now().getDayOfMonth();
        List<Subscription> due = subscriptionService.getDueToday(today);

        if (due.isEmpty()) return;

        log.info("Processing {} subscriptions due on day {}", due.size(), today);

        for (Subscription sub : due) {
            try {
                transactionService.addExpense(
                    sub.getUser(),
                    sub.getAmount(),
                    sub.getName() + " (подписка)"
                );

                bot.sendMessage(
                    sub.getUser().getTelegramId(),
                    "🔄 Подписка списана:\n\n" +
                    "📝 " + sub.getName() + "\n" +
                    String.format("💸 -%.0f ₸", sub.getAmount()),
                    KeyboardFactory.mainMenu()
                );

                log.debug("Subscription charged: {} for user {}",
                    sub.getName(), sub.getUser().getTelegramId());

            } catch (Exception e) {
                log.error("Failed to charge subscription {} for user {}: {}",
                    sub.getId(), sub.getUser().getTelegramId(), e.getMessage());
            }
        }
    }
}
