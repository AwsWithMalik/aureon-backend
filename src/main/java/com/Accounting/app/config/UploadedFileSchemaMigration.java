package com.Accounting.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UploadedFileSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public UploadedFileSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!uploadedFileDocumentTypeColumnExists()) {
            return;
        }

        dropDocumentTypeCheckConstraints();
        migrateDocumentTypeToString();
    }

    private boolean uploadedFileDocumentTypeColumnExists() {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'uploaded_file'
                  and column_name = 'document_type'
                """,
                Integer.class);

        return count != null && count > 0;
    }

    private void dropDocumentTypeCheckConstraints() {
        jdbcTemplate.execute(
                """
                do $$
                declare constraint_record record;
                begin
                    for constraint_record in
                        select c.conname
                        from pg_constraint c
                        join pg_class t on c.conrelid = t.oid
                        join pg_namespace n on t.relnamespace = n.oid
                        where n.nspname = current_schema()
                          and t.relname = 'uploaded_file'
                          and c.contype = 'c'
                          and pg_get_constraintdef(c.oid) like '%document_type%'
                    loop
                        execute format(
                            'alter table uploaded_file drop constraint if exists %I',
                            constraint_record.conname
                        );
                    end loop;
                end $$;
                """);
    }

    private void migrateDocumentTypeToString() {
        jdbcTemplate.execute(
                """
                alter table uploaded_file
                alter column document_type type varchar(255)
                using case document_type::text
                    when '0' then 'RECEIPT'
                    when '1' then 'INVOICE'
                    when '2' then 'BANK_STATEMENT'
                    when '3' then 'SPREADSHEET'
                    when '4' then 'TAX_DOCUMENT'
                    when '5' then 'OTHER'
                    else document_type::text
                end
                """);
    }
}
