package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class InvestmentSecurityQuoteSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public InvestmentSecurityQuoteSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("investment_securities")) {
            return;
        }

        jdbcTemplate.execute("alter table investment_securities add column if not exists current_price numeric(19,4)");
        jdbcTemplate.execute("alter table investment_securities add column if not exists previous_close_price numeric(19,4)");
        jdbcTemplate.execute("alter table investment_securities add column if not exists quote_timestamp timestamp");
        jdbcTemplate.execute("alter table investment_securities add column if not exists quote_source varchar(255)");
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
