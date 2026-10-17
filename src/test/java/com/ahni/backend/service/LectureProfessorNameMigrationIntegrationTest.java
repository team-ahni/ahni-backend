package com.ahni.backend.service;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class LectureProfessorNameMigrationIntegrationTest {
    @Test void migratesExistingNamesWithoutChangingLectureOrScheduleIdentities() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:18")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("24").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var sql = connection.createStatement()) {
                sql.execute("INSERT INTO course(id,code,name,credit,category) VALUES(1001,'MIG101','과목',3,'GENERAL_EDUCATION')");
                sql.execute("INSERT INTO professor(id,name) VALUES(1001,'남춘성,이우형')");
                sql.execute("INSERT INTO lecture(id,course_id,professor_id,academic_year,term,section) VALUES(1001,1001,1001,2026,'SECOND','001')");
                sql.execute("INSERT INTO lecture_schedule(id,lecture_id,day_of_week,start_time,end_time) VALUES(1001,1001,1,'09:00','10:00')");
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
                try (var result = sql.executeQuery("SELECT l.id,l.professor_name,s.id FROM lecture l JOIN lecture_schedule s ON s.lecture_id=l.id WHERE l.id=1001")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).isEqualTo(1001);
                    assertThat(result.getString(2)).isEqualTo("남춘성,이우형");
                    assertThat(result.getLong(3)).isEqualTo(1001);
                }
                try (var result = sql.executeQuery("SELECT to_regclass('public.professor')")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isNull();
                }
            }
        }
    }
}
