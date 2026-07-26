package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AppSettingsSecuritySchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public AppSettingsSecuritySchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("create table if not exists app_settings_security (settings_email varchar(255) not null primary key, mfa_enabled boolean default false, recovery_email varchar(255), recovery_phone varchar(255), backup_codes_enabled boolean default true, backup_codes_remaining integer default 8, constraint fk_app_settings_security_settings foreign key (settings_email) references app_settings(email))");
        jdbcTemplate.execute("alter table app_settings_security add column if not exists mfa_enabled boolean default false");
        jdbcTemplate.execute("update app_settings_security s set mfa_enabled = coalesce(s.mfa_enabled, a.mfa_enabled, false) from app_settings a where a.email = s.settings_email");
    }
}
