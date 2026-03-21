package com.monetka.scheduler;

import com.monetka.bot.MonetkaBot;
import com.monetka.bot.keyboard.KeyboardFactory;
import com.monetka.model.Subscription;
import com.monetka.service.SubscriptionService;
import com.monetka.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Subscription charges and expiry reminders.
 * Times are Asia/Bishkek (UTC+6):
 *   09:00 Bishkek = 03:00 UTC  (charges)
 *   10:00 Bishkek = 04:00 UTC  (reminders)
 */
@Component
public class SubscriptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionScheduler.class);

    private final SubscriptionService subscriptionService;
    private final TransactionService  transactionService;
    private final MonetkaBot          bot;

    public SubscriptionScheduler(SubscriptionService subscriptionService,
                                 TransactionService transactionService,
                                 MonetkaBot bot) {
        this.subscriptionService = subscriptionService;
        this.transactionService  = transactionService;
        this.bot                 = bot;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Bishkek")
    public void chargeSubscriptions() {
        List<Subscription> due = subscriptionService.getDueToday();
        if (due.isEmpty()) return;
        log.info("Charging {} subscriptions", due.size());
        for (Subscription sub : due) {
            try {
                transactionService.addExpense(sub.getUser(), sub.getAmount(),
                        sub.getName() + " (подписка)");
                bot.sendMessage(sub.getUser().getTelegramId(),
                        "🔄 *Списание подписки*\n\n📝 " + sub.getName() +
                                String.format("\n💸 −%,.0f сом", sub.getAmount()),
                        KeyboardFactory.mainMenu());
            } catch (Exception e) {
                log.error("Failed to charge subscription {}: {}", sub.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Bishkek")
    public void remindExpiring() {
        List<Subscription> expiring = subscriptionService.getExpiringSoon(3);
        for (Subscription sub : expiring) {
            Long days = sub.daysUntilExpiry();
            if (days == null) continue;
            String safeName = com.monetka.bot.MonetkaBot.esc(sub.getName());
            String msg = days == 0
                    ? "⚠️ *Подписка истекает сегодня!*\n📝 " + safeName
                    : "⏰ *Подписка истекает через " + days + " дн.*\n📝 " + safeName;
            bot.sendMarkdown(sub.getUser().getTelegramId(), msg);
        }
    }
}