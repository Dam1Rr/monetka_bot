package com.monetka.scheduler;

import com.monetka.bot.MonetkaBot;
import com.monetka.model.User;
import com.monetka.service.ReportService;
import com.monetka.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sends daily expense reports to all active users at 21:00 Asia/Bishkek (UTC+6).
 * Cron uses UTC: 21:00 Bishkek = 15:00 UTC
 */
@Component
public class DailyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyReportScheduler.class);

    private final UserService   userService;
    private final ReportService reportService;
    private final MonetkaBot    bot;

    public DailyReportScheduler(UserService userService,
                                ReportService reportService,
                                MonetkaBot bot) {
        this.userService   = userService;
        this.reportService = reportService;
        this.bot           = bot;
    }

    // 21:00 Asia/Bishkek = 15:00 UTC
    @Scheduled(cron = "0 0 15 * * *", zone = "Asia/Bishkek")
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