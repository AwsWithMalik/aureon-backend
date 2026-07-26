package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaxProfileConfigSchemaMigration implements ApplicationRunner {
    private static final String TAX_PROFILE_USER_EMAIL_FK = "fk_tax_profile_configs_user_email";

    private final JdbcTemplate jdbcTemplate;

    public TaxProfileConfigSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tableExists("tax_profile_configs")) {
            ensureRawConfigJsonColumn();
        }

        if (!tableExists("tax_profile_configs") || !tableExists("app_users") || constraintExists(TAX_PROFILE_USER_EMAIL_FK)) {
            return;
        }

        ensureUserEmailIsUnique();
        jdbcTemplate.execute(
                """
                delete from tax_profile_configs config
                where not exists (
                    select 1
                    from app_users users
                    where users.email = config.email
                )
                """);

        jdbcTemplate.execute(
                """
                alter table tax_profile_configs
                add constraint fk_tax_profile_configs_user_email
                foreign key (email)
                references app_users(email)
                on update cascade
                on delete cascade
                """);
    }

    private void ensureRawConfigJsonColumn() {
        if (columnExists("tax_profile_configs", "raw_config_json")) {
            return;
        }

        jdbcTemplate.execute(
                """
                alter table tax_profile_configs
                add column raw_config_json text
                """);
    }

    private void ensureUserEmailIsUnique() {
        if (uniqueConstraintExists("app_users", "email")) {
            return;
        }

        jdbcTemplate.execute(
                """
                alter table app_users
                add constraint uk_app_users_email
                unique (email)
                """);
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

    private boolean constraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.table_constraints
                where table_schema = current_schema()
                  and constraint_name = ?
                """,
                Integer.class,
                constraintName);

        return count != null && count > 0;
    }

    private boolean uniqueConstraintExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.table_constraints constraints
                join information_schema.key_column_usage columns
                  on constraints.constraint_name = columns.constraint_name
                 and constraints.table_schema = columns.table_schema
                 and constraints.table_name = columns.table_name
                where constraints.table_schema = current_schema()
                  and constraints.table_name = ?
                  and constraints.constraint_type in ('UNIQUE', 'PRIMARY KEY')
                  and columns.column_name = ?
                """,
                Integer.class,
                tableName,
                columnName);

        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = ?
                  and column_name = ?
                """,
                Integer.class,
                tableName,
                columnName);

        return count != null && count > 0;
    }
}
