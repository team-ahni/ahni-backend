package com.ahni.backend.service;

import com.ahni.backend.domain.*;
import com.ahni.backend.dto.*;
import com.ahni.backend.entity.*;
import com.ahni.backend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Tag("integration")
class CurriculumConcurrencyIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");
    static { POSTGRES.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @AfterAll static void stopPostgres() { POSTGRES.stop(); }
    @Autowired PlatformTransactionManager transactions;
    @Autowired CurriculumService curricula;
    @Autowired GradeService grades;
    @Autowired DepartmentRepository departments;
    @Autowired CourseRepository courses;
    @Autowired LectureRepository lectures;
    @Autowired JdbcTemplate jdbc;
    UUID admin;
    UUID student;
    UUID course;
    CurriculumResponse curriculum;
    int year;

    @BeforeEach void seed() {
        year = 2024 + jdbc.queryForObject("select count(*) from curriculum", Integer.class);
        admin = UUID.randomUUID(); student = UUID.randomUUID();
        jdbc.update("insert into admin(auth_user_id, name, email) values (?, '관리자', ?)", admin, admin + "@inha.edu");
        jdbc.update("insert into student(auth_user_id, email, admission_year, enrollment_status) values (?, ?, 2024, 'ENROLLED')", student, student + "@inha.edu");
        var department = departments.saveAndFlush(new Department("동시성" + year));
        course = courses.saveAndFlush(new Course(department, "LOCK" + year, "기초", new BigDecimal("3.0"), CourseCategory.MAJOR)).getEntityId();
        lectures.saveAndFlush(new Lecture(courses.findByEntityId(course).orElseThrow(), "교수", year, AcademicTerm.FIRST, "001"));
        var draft = curricula.create(admin, new CurriculumRequest(department.getEntityId(), year, "동시성 테스트 자료", null, null,
            List.of(new CurriculumCourseRequest(course, CurriculumDivision.MAJOR_REQUIRED, null, null, null, null, null, null))));
        curriculum = curricula.publish(admin, draft.entityId(), new CurriculumPublicationRequest(true, draft.version()));
    }

    @Test void publicationDoesNotBlockOfferedCourseRegistration() throws Exception {
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var publication = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                var result = curricula.publish(admin, curriculum.entityId(), new CurriculumPublicationRequest(false, curriculum.version()));
                held.countDown(); await(release); return result;
            }));
            try {
                assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
                var registration = workers.submit(() -> grades.register(student, request()));
                assertThat(registration.get(10, TimeUnit.SECONDS)).isNotNull();
                release.countDown();
                assertThat(publication.get(10, TimeUnit.SECONDS).published()).isFalse();
                assertThat(grades.getGrades(student)).hasSize(1);
            } finally { release.countDown(); }
        }
    }

    private GradeRegistrationRequest request() {
        return new GradeRegistrationRequest(course, year, AcademicTerm.FIRST, GradeCode.A_PLUS, new BigDecimal("3.0"), false, null);
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("transaction was not released"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
}
