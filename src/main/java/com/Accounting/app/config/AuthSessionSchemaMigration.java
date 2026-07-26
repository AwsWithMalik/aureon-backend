package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public AuthSessionSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(
                """
                create table if not exists auth_sessions (
                    id uuid primary key,
                    user_id integer not null,
                    refresh_token_hash varchar(64) not null unique,
                    previous_refresh_token_hash varchar(64),
                    created_at timestamp with time zone not null,
                    last_used_at timestamp with time zone not null,
                    expires_at timestamp with time zone not null,
                    revoked_at timestamp with time zone,
                    constraint fk_auth_session_user
                        foreign key (user_id)
                        references app_users(id)
                        on delete cascade
                )
                """);
        jdbcTemplate.execute(
                "alter table auth_sessions add column if not exists "
                        + "previous_refresh_token_hash varchar(64)");
        jdbcTemplate.execute(
                "create unique index if not exists idx_auth_session_refresh_hash "
                        + "on auth_sessions(refresh_token_hash)");
        jdbcTemplate.execute(
                "create index if not exists idx_auth_session_user_id "
                        + "on auth_sessions(user_id)");
    }
}
