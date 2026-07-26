package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransferMatchSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public TransferMatchSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("transactions")) {
            return;
        }

        jdbcTemplate.execute("alter table transactions add column if not exists transfer_group_id varchar(255)");
        jdbcTemplate.execute("alter table transactions add column if not exists matched_transfer_transaction_id integer");
        jdbcTemplate.execute("alter table transactions add column if not exists transfer_match_status varchar(255)");
        jdbcTemplate.execute("alter table transactions add column if not exists transfer_match_confidence float8");
        jdbcTemplate.execute("alter table transactions add column if not exists transfer_match_reason varchar(1000)");
        jdbcTemplate.execute("alter table transactions add column if not exists user_confirmed_transfer boolean");
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
