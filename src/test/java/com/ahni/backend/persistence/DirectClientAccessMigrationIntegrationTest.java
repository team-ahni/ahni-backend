package com.ahni.backend.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("integration")
class DirectClientAccessMigrationIntegrationTest {
    private static final String[] CLIENT_ROLES = {"anon", "authenticated"};

    @Test
    void blocksSupabaseClientRolesOnEveryPublicTableWhileBackendKeepsAccess() throws SQLException {
        try (var postgres = new PostgreSQLContainer<>("postgres:18")) {
            postgres.start();
            try (Connection connection = connection(postgres); Statement sql = connection.createStatement()) {
                // Supabase처럼 public 표가 생기면 클라이언트 역할에 모든 권한이 붙게 만든다.
                sql.executeUpdate("CREATE ROLE anon NOLOGIN");
                sql.executeUpdate("CREATE ROLE authenticated NOLOGIN");
                sql.executeUpdate("GRANT USAGE ON SCHEMA public TO anon, authenticated");
                sql.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated");
                sql.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated");
            }
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();

            try (Connection connection = connection(postgres); Statement sql = connection.createStatement()) {
                assertEquals("", text(sql, """
                    SELECT coalesce(string_agg(c.relname, ', '), '') FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND NOT c.relrowsecurity
                      -- 테스트 전용 R__ 마커는 V29 뒤에 생긴다. 권한이 없는지는 아래에서 검사한다.
                      AND c.relname NOT IN ('flyway_schema_history', 'harness_repeatable_marker')
                    """));
                sql.executeUpdate("CREATE TABLE public.after_block_probe (id bigint)");
                for (String role : CLIENT_ROLES) {
                    assertEquals("", text(sql, """
                        SELECT coalesce(string_agg(c.relname, ', '), '') FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'S')
                          AND (has_table_privilege('%1$s', c.oid, 'SELECT, INSERT, UPDATE, DELETE')
                               OR (c.relkind = 'S' AND has_sequence_privilege('%1$s', c.oid, 'USAGE')))
                        """.formatted(role)), role);
                }

                sql.executeUpdate("""
                    INSERT INTO student(auth_user_id, email, admission_year, enrollment_status)
                    VALUES (gen_random_uuid(), 'rls@inha.edu', 2024, 'ENROLLED')
                    """);
                for (String role : CLIENT_ROLES) {
                    sql.execute("SET ROLE " + role);
                    assertSqlState(sql, "42501", "SELECT count(*) FROM student");
                    assertSqlState(sql, "42501", "SELECT count(*) FROM admin");
                    assertSqlState(sql, "42501", "UPDATE admin SET auth_user_id = gen_random_uuid()");
                    assertSqlState(sql, "42501", "UPDATE student SET email = 'stolen@inha.edu'");
                    sql.execute("RESET ROLE");
                }

                sql.executeUpdate("CREATE ROLE ahni_backend_probe NOLOGIN BYPASSRLS");
                sql.executeUpdate("GRANT ALL ON student TO ahni_backend_probe");
                sql.execute("SET ROLE ahni_backend_probe");
                assertEquals(1, scalar(sql, "SELECT count(*) FROM student"));
                assertEquals(1, sql.executeUpdate("UPDATE student SET email = 'backend@inha.edu'"));
                sql.execute("RESET ROLE");
            }
        }
    }

    private static void assertSqlState(Statement statement, String state, String sql) {
        assertEquals(state, assertThrows(SQLException.class, () -> statement.execute(sql)).getSQLState());
    }

    private static long scalar(Statement statement, String query) throws SQLException {
        try (var result = statement.executeQuery(query)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static String text(Statement statement, String query) throws SQLException {
        try (var result = statement.executeQuery(query)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static Connection connection(PostgreSQLContainer<?> postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
