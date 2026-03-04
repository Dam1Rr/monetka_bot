package com.monetka.scheduler;

import com.monetka.bot.MonetkaBot;
import com.monetka.model.User;
import com.monetka.service.ReportService;
import com.monetka.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sends daily expense reports to all active users at 21:00.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReportScheduler {

    private final UserService   userService;
    private final ReportService reportService;
    private final MonetkaBot    bot;

    // Cron: seconds minutes hours day month weekday
    // Every day at 21:00 UTC (adjust to your timezone)
    @Scheduled(cron = "0 0 21 * * *")
    public void sendDailyReports() {
        List<User> activeUsers = userService.getActiveUsers();
        log.info("Sending daily reports to {} users", activeUsers.size());

        for (User user : activeUsers) {
            try {
                String report = reportService.buildDailyReport(user);
                bot.sendMarkdown(user.getTelegramId(), report);
            } catch (Exception e) {
                log.error("Failed to send daily report to {}: {}",
                    user.getTelegramId(), e.getMessage());
            }
        }
    }
}
