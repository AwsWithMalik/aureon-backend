package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PlaidInstitutionMetadataSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public PlaidInstitutionMetadataSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("ALTER TABLE IF EXISTS plaid_item ADD COLUMN IF NOT EXISTS institution_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE IF EXISTS plaid_item ADD COLUMN IF NOT EXISTS institution_name VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE IF EXISTS plaid_item ADD COLUMN IF NOT EXISTS institution_primary_color VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE IF EXISTS plaid_item ADD COLUMN IF NOT EXISTS institution_url VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE IF EXISTS plaid_item ADD COLUMN IF NOT EXISTS institution_logo TEXT");
        jdbcTemplate.execute("ALTER TABLE IF EXISTS plaid_item ALTER COLUMN institution_logo TYPE TEXT");
        jdbcTemplate.execute("ALTER TABLE IF EXISTS accounts ADD COLUMN IF NOT EXISTS available_balance NUMERIC(19,2)");
    }
}
