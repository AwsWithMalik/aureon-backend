package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PlaidInvestmentSyncSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public PlaidInvestmentSyncSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("plaid_item")) {
            return;
        }

        jdbcTemplate.execute("alter table plaid_item add column if not exists last_investment_sync_attempt_at timestamp");
        jdbcTemplate.execute("alter table plaid_item add column if not exists last_investment_sync_success_at timestamp");
        jdbcTemplate.execute("alter table plaid_item add column if not exists last_investment_sync_failure_at timestamp");
        jdbcTemplate.execute("alter table plaid_item add column if not exists last_investment_sync_error varchar(1000)");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = current_schema()
                  and table_name = ?
                """,
                Integer.class,
                tableName);

        return count != null && count > 0;
    }
}
