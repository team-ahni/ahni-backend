package com.ahni.backend.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class PrimaryGraduationPolicyImportIntegrationTest {
    @Test
    void importsApprovedPrimaryPoliciesWithoutGuessingConditionalRequirements() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:18")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
            var manifest = new ObjectMapper().readTree(Files.readString(Path.of("data/graduation/2026-11-25/primary-policies.json")));
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                for (var course : manifest.path("courses")) {
                    try (var insert = connection.prepareStatement("INSERT INTO public.course(entity_id,code,name,credit,category) VALUES (?,?,?,?,?)")) {
                        insert.setObject(1, UUID.randomUUID()); insert.setString(2, course.path("code").asText());
                        insert.setString(3, course.path("name").asText()); insert.setBigDecimal(4, course.path("credit").decimalValue());
                        insert.setString(5, course.path("category").asText()); insert.executeUpdate();
                    }
                }
                String sql = Files.readString(Path.of("data/graduation/2026-11-25/import.sql"));
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) { statement.execute(sql); }
                connection.commit();
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                    SELECT count(*) AS policies,
                      count(*) FILTER (WHERE admission_year >= 2025 AND min_foundation_credit=15 AND min_department_credit=65 AND min_general_credit=25) AS separate_foundation,
                      count(*) FILTER (WHERE evaluation_complete) AS complete,
                      count(*) FILTER (WHERE major_type <> 'PRIMARY') AS other_majors
                    FROM public.graduation_requirement
                    """)) {
                    rows.next(); assertThat(rows.getInt("policies")).isEqualTo(23);
                    assertThat(rows.getInt("separate_foundation")).isEqualTo(10);
                    assertThat(rows.getInt("complete")).isZero(); assertThat(rows.getInt("other_majors")).isZero();
                }
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                    SELECT count(*) FROM public.required_course r JOIN public.course c ON c.id=r.course_id
                    WHERE c.code IN ('SEC4001','BNF2505','GEB1108','GEB1109','GED3018','GED5004')
                    """)) { rows.next(); assertThat(rows.getInt(1)).isZero(); }
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT count(*) FROM public.required_course")) {
                    rows.next(); assertThat(rows.getInt(1)).isEqualTo(246);
                }
                // Re-running must fail rather than overwrite reviewed source policies.
                assertThatThrownBy(() -> { try (var statement = connection.createStatement()) { statement.execute(sql); } })
                    .hasMessageContaining("refusing to overwrite");
                connection.rollback();
            }
        }
    }
}
