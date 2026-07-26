package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PlaidSnapshotSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public PlaidSnapshotSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tableExists("accounts")) {
            jdbcTemplate.execute("alter table accounts add column if not exists last_sync_attempt_at timestamp");
            jdbcTemplate.execute("alter table accounts add column if not exists last_sync_success_at timestamp");
            jdbcTemplate.execute("alter table accounts add column if not exists last_sync_failure_at timestamp");
            jdbcTemplate.execute("alter table accounts add column if not exists last_sync_error varchar(1000)");
            jdbcTemplate.execute("alter table accounts add column if not exists previous_balance numeric(19,2)");
            jdbcTemplate.execute("alter table accounts add column if not exists previous_available_balance numeric(19,2)");
        }

        if (tableExists("plaid_item")) {
            jdbcTemplate.execute("alter table plaid_item add column if not exists last_account_sync_attempt_at timestamp");
            jdbcTemplate.execute("alter table plaid_item add column if not exists last_account_sync_success_at timestamp");
            jdbcTemplate.execute("alter table plaid_item add column if not exists last_account_sync_failure_at timestamp");
            jdbcTemplate.execute("alter table plaid_item add column if not exists last_account_sync_error varchar(1000)");
        }

        jdbcTemplate.execute("create table if not exists account_balance_snapshots (id serial primary key, email varchar(255), account_id varchar(255), account_name varchar(255), account_type varchar(255), account_subtype varchar(255), balance numeric(19,2), available_balance numeric(19,2), currency varchar(32), snapshot_at timestamp, plaid_item_id integer)");
        jdbcTemplate.execute("create index if not exists idx_account_balance_snapshots_email_account_time on account_balance_snapshots(email, account_id, snapshot_at)");

        jdbcTemplate.execute("create table if not exists investment_portfolio_snapshots (id serial primary key, email varchar(255), account_id varchar(255), account_name varchar(255), total_value numeric(19,2), currency varchar(32), snapshot_at timestamp, plaid_item_id integer)");
        jdbcTemplate.execute("create index if not exists idx_investment_portfolio_snapshots_email_account_time on investment_portfolio_snapshots(email, account_id, snapshot_at)");
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
