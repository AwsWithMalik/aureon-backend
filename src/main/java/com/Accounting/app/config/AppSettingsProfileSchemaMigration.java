package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AppSettingsProfileSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public AppSettingsProfileSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists display_name varchar(255)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists phone varchar(255)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists date_of_birth date");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists address_line1 varchar(255)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists city varchar(255)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists region varchar(255)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists country varchar(255)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists language varchar(255)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists timezone varchar(255)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists date_format varchar(255)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists communication_preference varchar(255)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists member_since date");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists email_verified boolean default false");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists phone_verified boolean default false");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists last_login_at timestamp");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists quiet_hours_enabled boolean default true");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists quiet_hours_start varchar(20)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists quiet_hours_end varchar(20)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists digest_frequency varchar(40)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists digest_day varchar(40)");
        jdbcTemplate.execute("alter table if exists app_settings add column if not exists digest_time varchar(20)");
    }
}
