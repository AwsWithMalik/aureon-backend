package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TeamInvitationSchemaMigration implements ApplicationRunner {
    private static final String TEAM_INVITATIONS_USER_EMAIL_FK = "fk_team_invitations_workspace_email";

    private final JdbcTemplate jdbcTemplate;

    public TeamInvitationSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("team_invitations")) {
            return;
        }

        addColumnIfMissing("team_invitations", "invite_token_hash", "varchar(255)");
        addColumnIfMissing("team_invitations", "accepted_at", "timestamp");
        addColumnIfMissing("team_invitations", "email_sent_at", "timestamp");
        addColumnIfMissing("team_invitations", "email_delivery_status", "varchar(255)");
        addColumnIfMissing("team_invitations", "email_delivery_error", "varchar(1000)");
        addUniqueIndexIfMissing("team_invitations", "invite_token_hash", "idx_team_invitations_invite_token_hash");

        if (!tableExists("app_users") || constraintExists(TEAM_INVITATIONS_USER_EMAIL_FK)) {
            return;
        }

        ensureUserEmailIsUnique();
        jdbcTemplate.execute(
                """
                delete from team_invitations invitations
                where not exists (
                    select 1
                    from app_users users
                    where users.email = invitations.workspace_email
                )
                """);

        jdbcTemplate.execute(
                """
                alter table team_invitations
                add constraint fk_team_invitations_workspace_email
                foreign key (workspace_email)
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

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        if (columnExists(tableName, columnName)) {
            return;
        }

        jdbcTemplate.execute("alter table " + tableName + " add column " + columnName + " " + definition);
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

    private void addUniqueIndexIfMissing(String tableName, String columnName, String indexName) {
        if (indexExists(indexName)) {
            return;
        }

        jdbcTemplate.execute("create unique index " + indexName + " on " + tableName + " (" + columnName + ") where " + columnName + " is not null");
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from pg_indexes
                where schemaname = current_schema()
                  and indexname = ?
                """,
                Integer.class,
                indexName);

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
