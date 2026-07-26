package com.Accounting.app.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PlaidUserScopeSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public PlaidUserScopeSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        dropUniqueConstraintsForColumn("accounts", "plaid_account_id");
        dropUniqueIndexesForColumn("accounts", "plaid_account_id");
        dropUniqueConstraintsForColumn("transactions", "plaid_transaction_id");
        dropUniqueIndexesForColumn("transactions", "plaid_transaction_id");
    }

    private void dropUniqueConstraintsForColumn(String tableName, String columnName) {
        List<ConstraintRef> constraints = jdbcTemplate.query(
                """
                select distinct tc.table_name, tc.constraint_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_schema = kcu.constraint_schema
                 and tc.table_name = kcu.table_name
                 and tc.constraint_name = kcu.constraint_name
                where tc.table_schema = current_schema()
                  and lower(tc.table_name) = lower(?)
                  and tc.constraint_type = 'UNIQUE'
                  and kcu.column_name = ?
                """,
                (rs, rowNum) -> new ConstraintRef(rs.getString("table_name"), rs.getString("constraint_name")),
                tableName,
                columnName);

        for (ConstraintRef constraint : constraints) {
            jdbcTemplate.execute("alter table " + quoteIdentifier(constraint.tableName())
                    + " drop constraint if exists " + quoteIdentifier(constraint.constraintName()));
        }
    }

    private void dropUniqueIndexesForColumn(String tableName, String columnName) {
        List<String> indexNames = jdbcTemplate.query(
                """
                select distinct index_class.relname as index_name
                from pg_index idx
                join pg_class table_class on table_class.oid = idx.indrelid
                join pg_namespace ns on ns.oid = table_class.relnamespace
                join pg_class index_class on index_class.oid = idx.indexrelid
                join pg_attribute attr on attr.attrelid = table_class.oid
                 and attr.attnum = any(idx.indkey)
                where ns.nspname = current_schema()
                  and lower(table_class.relname) = lower(?)
                  and attr.attname = ?
                  and idx.indisunique = true
                  and idx.indisprimary = false
                """,
                (rs, rowNum) -> rs.getString("index_name"),
                tableName,
                columnName);

        for (String indexName : indexNames) {
            jdbcTemplate.execute("drop index if exists " + quoteIdentifier(indexName));
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record ConstraintRef(String tableName, String constraintName) {
    }
}
