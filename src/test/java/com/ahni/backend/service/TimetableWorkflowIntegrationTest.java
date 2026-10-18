package com.ahni.backend.service;

import com.ahni.backend.domain.*;
import com.ahni.backend.dto.*;
import com.ahni.backend.entity.*;
import com.ahni.backend.exception.*;
import com.ahni.backend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Tag("integration")
@Transactional
class TimetableWorkflowIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = startPostgres();

    private static PostgreSQLContainer<?> startPostgres() {
        var postgres = new PostgreSQLContainer<>("postgres:18");
        postgres.start();
        return postgres;
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterAll static void stopPostgres() { POSTGRES.stop(); }

    @Autowired TimetableService service;
    @Autowired StudentRepository students;
    @Autowired CourseRepository courses;
    @Autowired LectureRepository lectures;
    @Autowired LectureService lectureService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void semesterLookupFetchesCoursesAndSchedulesInOneQueryIncludingUntimedOfferings() {
        var first = lecture("남춘성,이우형", "FETCH_A", 19, 22);
        first.addSchedule(6, LocalTime.of(9, 0), LocalTime.of(12, 0), "M-301");
        lectures.saveAndFlush(first);
        lecture("교수", "FETCH_B", 9, 12);
        var course = courses.saveAndFlush(new Course(null, "FETCH_WEB", "금융시장론", new BigDecimal("3.0"), CourseCategory.MAJOR));
        lectures.saveAndFlush(new Lecture(course, "이기환", 2026, AcademicTerm.SECOND, "001"));
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            var found = lectureService.list(2026, AcademicTerm.SECOND).stream()
                .filter(row -> row.courseCode().startsWith("FETCH_")).toList();
            assertThat(found).hasSize(3);
            assertThat(found.get(0).schedules()).hasSize(2);
            assertThat(found.get(2).schedules()).isEmpty();
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    Lecture lecture(String professor, String code, int start, int end) {
        var course = courses.saveAndFlush(new Course(null, code, code, new BigDecimal("3.0"), CourseCategory.MAJOR));
        var lecture = new Lecture(course, professor, 2026, AcademicTerm.SECOND, "001");
        lecture.addSchedule(1, LocalTime.of(start, 0), LocalTime.of(end, 0), "101호");
        return lectures.saveAndFlush(lecture);
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void bulkImportRollsBackEarlierOfferingsWhenLaterOneConflicts() {
        UUID adminUser = UUID.randomUUID();
        jdbc.update("insert into admin (auth_user_id, name, email) values (?, ?, ?)",
            adminUser, "관리자", adminUser + "@inha.edu");
        var professor = "일괄 등록 교수";
        var existing = lecture(professor, "BULK_DUP", 9, 10);
        var course = courses.saveAndFlush(new Course(null, "BULK_NEW", "새 강의", new BigDecimal("3.0"), CourseCategory.MAJOR));
        var times = List.of(new LectureScheduleRequest(1, LocalTime.of(9, 0), LocalTime.of(10, 0), null));
        var first = new LectureRequest(course.getEntityId(), professor, 2026, AcademicTerm.SECOND, "001", times);
        var duplicate = new LectureRequest(existing.getCourse().getEntityId(), professor,
            2026, AcademicTerm.SECOND, "001", times);
        assertThatThrownBy(() -> lectureService.importLectures(adminUser, new LectureImportRequest(List.of(first, duplicate))))
            .isInstanceOf(TimetableConflictException.class);
        assertThat(lectures.existsByCourseIdAndAcademicYearAndTermAndSection(course.getId(),
            2026, AcademicTerm.SECOND, "001")).isFalse();
        assertThat(lectureService.importLectures(adminUser, new LectureImportRequest(List.of(first)))).hasSize(1);
    }

    @Test void persistsSemesterEntriesAndRejectsOverlapAndForeignOwner() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        students.saveAndFlush(new Student(user, user + "@inha.edu", 2024, EnrollmentStatus.ENROLLED, "학생"));
        students.saveAndFlush(new Student(other, other + "@inha.edu", 2024, EnrollmentStatus.ENROLLED, "다른 학생"));
        var professor = "교수";
        var first = lecture(professor, "TT101", 9, 10);
        var adjacent = lecture(professor, "TT102", 10, 11);
        var overlap = lecture(professor, "TT103", 9, 11);
        var entry = service.create(user, new TimetableRequest(first.getEntityId()));
        assertThat(entry.createdAt()).isNotNull();
        assertThat(service.list(user, 2026, AcademicTerm.SECOND)).hasSize(1);
        assertThat(service.list(user, 2026, AcademicTerm.FIRST)).isEmpty();
        assertThat(service.list(other, 2026, AcademicTerm.SECOND)).isEmpty();
        assertThatThrownBy(() -> service.delete(other, entry.entityId())).isInstanceOf(TimetableNotFoundException.class);
        assertThatThrownBy(() -> service.create(user, new TimetableRequest(overlap.getEntityId())))
            .isInstanceOf(TimetableConflictException.class);
        service.update(user, entry.entityId(), new TimetableRequest(adjacent.getEntityId()));
        assertThat(service.list(user, 2026, AcademicTerm.SECOND).getFirst().lecture().courseCode()).isEqualTo("TT102");
        service.delete(user, entry.entityId());
        assertThat(service.list(user, 2026, AcademicTerm.SECOND)).isEmpty();
        assertThat(lectures.findByEntityId(adjacent.getEntityId())).isPresent();
    }
}
