package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationDeliverySchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public NotificationDeliverySchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(
                """
                create table if not exists notification_follow_up_delivery (
                    id bigserial primary key,
                    settings_email varchar(255) not null,
                    notification_type varchar(255) not null,
                    period_key varchar(255) not null,
                    scheduled_for timestamp,
                    delivered_at timestamp,
                    delivery_status varchar(255),
                    error_message varchar(1000)
                )
                """);

        jdbcTemplate.execute(
                """
                create unique index if not exists uk_notification_follow_up_delivery_period
                on notification_follow_up_delivery (settings_email, notification_type, period_key)
                """);
    }
}
