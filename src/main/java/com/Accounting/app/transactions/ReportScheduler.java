package com.Accounting.app.transactions;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.reports.scheduler.enabled", havingValue = "true")
public class ReportScheduler {
    @Scheduled(fixedRate = 5000)
    public void generateDailySummary() {
        System.out.println("Every 5 seconds");
    }

    @Scheduled(fixedDelay = 5000)
    public void cleanupOldLogs() {
        System.out.println("5 seconds after the last function finished");
    }

    @Scheduled(cron = "0 0 0 1 * *")
    public void generateMonthlyReports() {
        System.out.println("the first of every month");
    }

}
