package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AppSettingsSchemaMigration implements ApplicationRunner {
    private static final String SETTINGS_USER_EMAIL_FK = "fk_app_settings_user_email";

    private final JdbcTemplate jdbcTemplate;

    public AppSettingsSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("app_settings") || !tableExists("app_users") || constraintExists(SETTINGS_USER_EMAIL_FK)) {
            return;
        }

        ensureUserEmailIsUnique();
        jdbcTemplate.execute(
                """
                delete from app_settings settings
                where not exists (
                    select 1
                    from app_users users
                    where users.email = settings.email
                )
                """);

        jdbcTemplate.execute(
                """
                alter table app_settings
                add constraint fk_app_settings_user_email
                foreign key (email)
                references app_users(email)
                on update cascade
                on delete cascade
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
}
