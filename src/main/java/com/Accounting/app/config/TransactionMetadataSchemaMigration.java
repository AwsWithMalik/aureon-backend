package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionMetadataSchemaMigration implements ApplicationRunner {
    private static final String TRANSACTION_METADATA_FK = "fk_transaction_metadata_transaction";

    private final JdbcTemplate jdbcTemplate;

    public TransactionMetadataSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("transaction_metadata") || !tableExists("transactions")) {
            return;
        }

        jdbcTemplate.execute(
                """
                delete from transaction_metadata metadata
                where metadata.transaction_transaction_id is null
                   or not exists (
                       select 1
                       from transactions transactions
                       where transactions.transaction_id = metadata.transaction_transaction_id
                   )
                """);

        if (constraintExists(TRANSACTION_METADATA_FK)) {
            return;
        }

        jdbcTemplate.execute(
                """
                alter table transaction_metadata
                add constraint fk_transaction_metadata_transaction
                foreign key (transaction_transaction_id)
                references transactions(transaction_id)
                on delete cascade
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

    private boolean constraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.table_constraints
                where table_schema = current_schema()
                  and lower(constraint_name) = lower(?)
                """,
                Integer.class,
                constraintName);

        return count != null && count > 0;
    }
}
