package com.Accounting.app.settings;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationWeeklyFollowUpScheduler {
    private final NotificationWeeklyFollowUpService notificationWeeklyFollowUpService;

    public NotificationWeeklyFollowUpScheduler(NotificationWeeklyFollowUpService notificationWeeklyFollowUpService) {
        this.notificationWeeklyFollowUpService = notificationWeeklyFollowUpService;
    }

    @Scheduled(cron = "${app.notifications.weekly-follow-up.scan-cron:0 */5 * * * *}")
    public void dispatchDueWeeklyFollowUps() {
        notificationWeeklyFollowUpService.dispatchDueWeeklyFollowUps();
    }
}
