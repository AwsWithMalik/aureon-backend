package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionMerchantSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public TransactionMerchantSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("transactions")) {
            return;
        }

        jdbcTemplate.execute("alter table transactions add column if not exists raw_merchant_name varchar(255)");
        jdbcTemplate.execute("alter table transactions add column if not exists display_merchant_name varchar(255)");
        jdbcTemplate.execute("alter table transactions add column if not exists plaid_name varchar(500)");
        jdbcTemplate.execute("alter table transactions add column if not exists website varchar(500)");
        jdbcTemplate.execute("alter table transactions add column if not exists logo_url varchar(1000)");

        if (columnExists("transactions", "merchant_name")) {
            jdbcTemplate.execute(
                    """
                    update transactions
                    set raw_merchant_name = merchant_name
                    where raw_merchant_name is null
                      and merchant_name is not null
                    """);
            jdbcTemplate.execute(
                    """
                    update transactions
                    set display_merchant_name = merchant_name
                    where display_merchant_name is null
                      and merchant_name is not null
                    """);
        }

        jdbcTemplate.execute(
                """
                update transactions
                set display_merchant_name = description
                where display_merchant_name is null
                  and description is not null
                """);
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

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where table_schema = current_schema()
                  and lower(table_name) = lower(?)
                  and lower(column_name) = lower(?)
                """,
                Integer.class,
                tableName,
                columnName);

        return count != null && count > 0;
    }
}
