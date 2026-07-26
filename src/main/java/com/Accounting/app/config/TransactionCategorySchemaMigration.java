package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionCategorySchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public TransactionCategorySchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("transactions")) {
            return;
        }

        jdbcTemplate.execute("alter table transactions add column if not exists plaid_category_primary varchar(255)");
        jdbcTemplate.execute("alter table transactions add column if not exists plaid_category_detailed varchar(255)");
        jdbcTemplate.execute("alter table transactions add column if not exists display_category varchar(255)");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = current_schema()
                  and lower(table_name) = lower(?)
                """,
                Integer.class,
                tableName);

        return count != null && count > 0;
    }
}
