package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AgentWorkspaceSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public AgentWorkspaceSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(
                """
                create table if not exists agent_session (
                    id varchar(64) primary key,
                    user_email varchar(255) not null,
                    title varchar(255) not null,
                    preview varchar(1000),
                    updated_at timestamp with time zone not null
                )
                """);

        jdbcTemplate.execute(
                """
                create table if not exists agent_folder (
                    id varchar(64) primary key,
                    user_email varchar(255) not null,
                    name varchar(255) not null,
                    color varchar(32),
                    created_at timestamp with time zone not null
                )
                """);

        jdbcTemplate.execute("alter table agent_session add column if not exists folder_id varchar(64)");
        Integer folderConstraintCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.table_constraints
                where table_name = 'agent_session'
                  and constraint_name = 'fk_agent_session_folder'
                """,
                Integer.class);
        if (folderConstraintCount == null || folderConstraintCount == 0) {
            jdbcTemplate.execute(
                    """
                    alter table agent_session
                    add constraint fk_agent_session_folder
                    foreign key (folder_id)
                    references agent_folder(id)
                    on delete set null
                    """);
        }

        jdbcTemplate.execute(
                """
                create table if not exists agent_session_message (
                    id varchar(64) primary key,
                    session_id varchar(64) not null,
                    role varchar(32) not null,
                    content text not null,
                    created_at timestamp with time zone not null,
                    constraint fk_agent_session_message_session
                        foreign key (session_id)
                        references agent_session(id)
                        on delete cascade
                )
                """);

        jdbcTemplate.execute("alter table if exists agent_session_message alter column content type text");
        jdbcTemplate.execute("alter table if exists agent_session_message add column if not exists upload_request text");
        jdbcTemplate.execute("alter table if exists agent_session_message alter column upload_request type text");

        jdbcTemplate.execute(
                """
                create table if not exists agent_memory (
                    id serial primary key,
                    user_id integer not null,
                    memory_type varchar(64) not null,
                    content text not null,
                    confidence double precision,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    constraint fk_agent_memory_user
                        foreign key (user_id)
                        references app_users(id)
                        on delete cascade
                )
                """);

        jdbcTemplate.execute("alter table if exists agent_session_message_files alter column upload_request type text");
        jdbcTemplate.execute("alter table if exists agent_session_message_files alter column extracted_data type text");
    }
}
