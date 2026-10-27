package com.ahni.backend.controller;

import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Department;
import com.ahni.backend.repository.CourseRepository;
import com.ahni.backend.repository.DepartmentRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class AdminCurriculumControllerTest {
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
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired DepartmentRepository departments;
    @Autowired CourseRepository courses;
    @Autowired com.ahni.backend.repository.LectureRepository lectures;
    @Autowired com.ahni.backend.repository.StudentRepository students;
    @Autowired com.ahni.backend.repository.StudentMajorRepository majors;
    @Autowired com.ahni.backend.repository.StudentGradeRepository grades;
    @Autowired com.ahni.backend.repository.GraduationRequirementRepository requirements;
    UUID admin;
    UUID department;
    UUID course;

    @BeforeEach void seed() {
        admin = UUID.randomUUID();
        jdbc.update("INSERT INTO admin(auth_user_id, name, email) VALUES (?, '관리자', ?)", admin, admin + "@inha.edu");
        var savedDepartment = departments.saveAndFlush(new Department("API교과과정학과"));
        department = savedDepartment.getEntityId();
        course = courses.saveAndFlush(new Course(savedDepartment, "PLAN101", "기초", new BigDecimal("3.0"), CourseCategory.MAJOR)).getEntityId();
    }

    @Test void unauthenticatedAndNonAdminCannotAccess() throws Exception {
        mvc.perform(get("/api/v1/admin/curricula")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/curricula").with(jwt().jwt(t -> t.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test void sharedMajorCourseIsRecognizedByEachDepartmentWithoutDuplicatingTheCourse() throws Exception {
        var other = departments.saveAndFlush(new Department("공통전공인정학과"));
        var first = create(input(null, course));
        var second = create(input(null, course).replace(department.toString(), other.getEntityId().toString()));
        for (var draft : java.util.List.of(first, second)) {
            mvc.perform(put("/api/v1/admin/curricula/" + draft.path("entityId").asText() + "/publication")
                .with(jwt().jwt(t -> t.subject(admin.toString()))).contentType("application/json")
                .content("{\"published\":true,\"version\":0}")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/curriculum-courses").param("academicYear", "2024").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].entityId").value(course.toString()));
        for (UUID recognizedDepartment : java.util.List.of(department, other.getEntityId())) {
            mvc.perform(get("/api/v1/curriculum-courses").param("academicYear", "2024")
                .param("departmentEntityId", recognizedDepartment.toString()).with(jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].entityId").value(course.toString()));
        }
    }

    @Test void sharedMajorProgressUsesPublishedAttendanceYearForPrimaryAndDoubleMajor() throws Exception {
        var other = departments.saveAndFlush(new Department("복수전공인정학과"));
        var first = create(input(null, course));
        var second = create(input(null, course).replace(department.toString(), other.getEntityId().toString()));
        for (var draft : java.util.List.of(first, second)) {
            mvc.perform(put("/api/v1/admin/curricula/" + draft.path("entityId").asText() + "/publication")
                .with(jwt().jwt(t -> t.subject(admin.toString()))).contentType("application/json")
                .content("{\"published\":true,\"version\":0}")).andExpect(status().isOk());
        }
        UUID studentAuth = UUID.randomUUID();
        var student = students.saveAndFlush(new com.ahni.backend.entity.Student(studentAuth, "shared@inha.edu", 2023,
            com.ahni.backend.domain.EnrollmentStatus.ENROLLED, "공통전공학생"));
        var primary = departments.findByEntityIdAndDeletedAtIsNull(department).orElseThrow();
        var shared = courses.findByEntityId(course).orElseThrow();
        for (var major : java.util.List.of(
            new com.ahni.backend.entity.StudentMajor(student, primary, com.ahni.backend.entity.MajorType.PRIMARY),
            new com.ahni.backend.entity.StudentMajor(student, other, com.ahni.backend.entity.MajorType.DOUBLE_MAJOR))) {
            majors.saveAndFlush(major);
            requirements.saveAndFlush(new com.ahni.backend.entity.GraduationRequirement(major.getDepartment(), 2023,
                major.getMajorType(), new BigDecimal("130.0"), new BigDecimal("60.0"), new BigDecimal("30.0"), "입학연도 기준", null));
        }
        grades.saveAndFlush(new com.ahni.backend.entity.StudentGrade(student, shared, 2024,
            com.ahni.backend.domain.AcademicTerm.FIRST, com.ahni.backend.domain.GradeCode.A_PLUS,
            new BigDecimal("3.0"), false, null));
        mvc.perform(get("/api/v1/graduation-progress").with(jwt().jwt(t -> t.subject(studentAuth.toString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].admissionYear").value(2023))
            .andExpect(jsonPath("$[0].credits.department.completed").value(3.0))
            .andExpect(jsonPath("$[1].credits.department.completed").value(3.0))
            .andExpect(jsonPath("$[0].credits.total.completed").value(3.0))
            .andExpect(jsonPath("$[1].credits.total.completed").value(3.0));
        mvc.perform(put("/api/v1/admin/curricula/" + second.path("entityId").asText() + "/publication")
            .with(jwt().jwt(t -> t.subject(admin.toString()))).contentType("application/json")
            .content("{\"published\":false,\"version\":1}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/graduation-progress").with(jwt().jwt(t -> t.subject(studentAuth.toString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].credits.department.completed").value(3.0))
            .andExpect(jsonPath("$[1].credits.department.completed").value(0.0))
            .andExpect(jsonPath("$[1].credits.total.completed").value(3.0));
    }

    @Test void createsDepartmentlessMajorThroughTheAdminApiButKeepsDivisionValidation() throws Exception {
        var created = mvc.perform(post("/api/v1/admin/courses").with(jwt().jwt(t -> t.subject(admin.toString())))
            .contentType("application/json")
            .content("{\"code\":\"SHARED101\",\"name\":\"공통기초\",\"credit\":3.0,\"category\":\"MAJOR\",\"departmentEntityId\":null}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.department").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        UUID sharedId = UUID.fromString(json.readTree(created).path("entityId").asText());
        var draft = create(input(null, sharedId));
        mvc.perform(put("/api/v1/admin/curricula/" + draft.path("entityId").asText())
            .with(jwt().jwt(t -> t.subject(admin.toString()))).contentType("application/json")
            .content(input(0L, sharedId).replace("MAJOR_REQUIRED", "GENERAL_REQUIRED")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void createsDraftPublishesAndRejectsStaleEdit() throws Exception {
        var created = create(input(null, course));
        String id = created.path("entityId").asText();
        mvc.perform(put("/api/v1/admin/curricula/" + id + "/publication").with(jwt().jwt(t -> t.subject(admin.toString())))
                .contentType("application/json").content("{\"published\":true,\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/admin/curricula/" + id).with(jwt().jwt(t -> t.subject(admin.toString())))
                .contentType("application/json").content(input(0L, course)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CURRICULUM_EDIT_CONFLICT"));
        mvc.perform(put("/api/v1/admin/curricula/" + id).with(jwt().jwt(t -> t.subject(admin.toString())))
                .contentType("application/json").content(input(1L, course)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(false)).andExpect(jsonPath("$.version").value(2));
    }

    @Test void duplicateYearAndEmptyPublicationHaveStableErrors() throws Exception {
        create(input(null, course));
        mvc.perform(post("/api/v1/admin/curricula").with(jwt().jwt(t -> t.subject(admin.toString())))
                .contentType("application/json").content(input(null, course)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CURRICULUM_ALREADY_EXISTS"));
        var empty = create("{\"departmentEntityId\":\"" + department + "\",\"curriculumYear\":2025,\"sourceTitle\":\"자료\",\"courses\":[]}");
        mvc.perform(put("/api/v1/admin/curricula/" + empty.path("entityId").asText() + "/publication")
                .with(jwt().jwt(t -> t.subject(admin.toString()))).contentType("application/json").content("{\"published\":true,\"version\":0}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void invalidReplacementPreservesPublishedAssignments() throws Exception {
        var created = create(input(null, course));
        String id = created.path("entityId").asText();
        mvc.perform(put("/api/v1/admin/curricula/" + id + "/publication").with(jwt().jwt(t -> t.subject(admin.toString())))
            .contentType("application/json").content("{\"published\":true,\"version\":0}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/curricula/" + id).with(jwt().jwt(t -> t.subject(admin.toString())))
            .contentType("application/json").content(input(1L, course).replace("}]}", "},{\"courseEntityId\":\"" + UUID.randomUUID() + "\",\"division\":\"MAJOR_REQUIRED\"}]}")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        mvc.perform(get("/api/v1/admin/curricula/" + id).with(jwt().jwt(t -> t.subject(admin.toString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true))
            .andExpect(jsonPath("$.courses[0].course.entityId").value(course.toString()));
    }

    @Test void yearQueryExcludesOtherYearsAndKeepsInactiveHistoricalCourses() throws Exception {
        var created = create(input(null, course));
        mvc.perform(get("/api/v1/curriculum-courses").param("academicYear", "2024").with(jwt()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CURRICULUM_NOT_AVAILABLE"));
        mvc.perform(put("/api/v1/admin/curricula/" + created.path("entityId").asText() + "/publication")
            .with(jwt().jwt(t -> t.subject(admin.toString()))).contentType("application/json")
            .content("{\"published\":true,\"version\":0}")).andExpect(status().isOk());
        courses.findByEntityId(course).orElseThrow().deactivate();
        courses.flush();
        mvc.perform(get("/api/v1/curriculum-courses").param("academicYear", "2024").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].entityId").value(course.toString()));
        mvc.perform(get("/api/v1/curriculum-courses").param("academicYear", "2025").with(jwt()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CURRICULUM_NOT_AVAILABLE"));
        mvc.perform(get("/api/v1/curriculum-courses").with(jwt())).andExpect(status().isBadRequest());
    }

    @Test void rejectsUnofferedYearButPreservesSameSemesterLegacyEdits() throws Exception {
        jdbc.update("INSERT INTO student(auth_user_id, email, admission_year, enrollment_status) VALUES (?, ?, 2024, 'ENROLLED')", admin, admin + "@inha.edu");
        var created = create(input(null, course));
        mvc.perform(put("/api/v1/admin/curricula/" + created.path("entityId").asText() + "/publication")
            .with(jwt().jwt(t -> t.subject(admin.toString()))).contentType("application/json")
            .content("{\"published\":true,\"version\":0}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/grades").with(jwt().jwt(t -> t.subject(admin.toString())))
            .contentType("application/json").content(gradeInput(2025)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_GRADE"));
        lectures.saveAndFlush(new com.ahni.backend.entity.Lecture(courses.findByEntityId(course).orElseThrow(), "교수", 2024, com.ahni.backend.domain.AcademicTerm.FIRST, "001"));
        courses.findByEntityId(course).orElseThrow().deactivate();
        courses.flush();
        var saved = mvc.perform(post("/api/v1/grades").with(jwt().jwt(t -> t.subject(admin.toString())))
            .contentType("application/json").content(gradeInput(2024)))
            .andExpect(status().isCreated()).andReturn();
        String gradeId = json.readTree(saved.getResponse().getContentAsString()).path("entityId").asText();
        mvc.perform(put("/api/v1/admin/curricula/" + created.path("entityId").asText() + "/publication")
            .with(jwt().jwt(t -> t.subject(admin.toString()))).contentType("application/json")
            .content("{\"published\":false,\"version\":1}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/grades/" + gradeId).with(jwt().jwt(t -> t.subject(admin.toString())))
            .contentType("application/json").content(gradeInput(2024))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/grades/" + gradeId).with(jwt().jwt(t -> t.subject(admin.toString())))
            .contentType("application/json").content(gradeInput(2025)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_GRADE"));
    }

    @Test void offeredCoursesAreDeduplicatedAndFilteredByTerm() throws Exception {
        var selected = courses.findByEntityId(course).orElseThrow();
        lectures.saveAndFlush(new com.ahni.backend.entity.Lecture(selected, "교수", 2024, com.ahni.backend.domain.AcademicTerm.FIRST, "001"));
        lectures.saveAndFlush(new com.ahni.backend.entity.Lecture(selected, "다른 교수", 2024, com.ahni.backend.domain.AcademicTerm.FIRST, "002"));
        mvc.perform(get("/api/v1/lectures/courses").param("academicYear", "2024").param("term", "FIRST").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].entityId").value(course.toString()))
            .andExpect(jsonPath("$[0].professorNames[0]").value("교수"))
            .andExpect(jsonPath("$[0].professorNames[1]").value("다른 교수"));
        mvc.perform(get("/api/v1/lectures/courses").param("academicYear", "2024").param("term", "SECOND").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/lectures/courses").param("academicYear", "2024").param("term", "FIRST"))
            .andExpect(status().isUnauthorized());
    }

    private String gradeInput(int year) {
        return "{\"courseEntityId\":\"" + course + "\",\"academicYear\":" + year
            + ",\"term\":\"FIRST\",\"gradeCode\":\"A_PLUS\",\"credit\":3.0,\"rpl\":false}";
    }

    @Test void sharedGeneralCourseIsDeduplicatedAndDepartmentFilterUsesCurriculumOwner() throws Exception {
        var other = departments.saveAndFlush(new Department("다른학과"));
        var general = courses.saveAndFlush(new Course(null, "GEB1107", "영어", new BigDecimal("3.0"), CourseCategory.GENERAL_EDUCATION)).getEntityId();
        var original = create(input(null, general).replace("MAJOR_REQUIRED", "GENERAL_REQUIRED"));
        var another = create(input(null, general).replace(department.toString(), other.getEntityId().toString()).replace("MAJOR_REQUIRED", "GENERAL_REQUIRED"));
        for (var row : new tools.jackson.databind.JsonNode[]{original, another}) {
            mvc.perform(put("/api/v1/admin/curricula/" + row.path("entityId").asText() + "/publication")
                .with(jwt().jwt(t -> t.subject(admin.toString()))).contentType("application/json")
                .content("{\"published\":true,\"version\":0}")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/curriculum-courses").param("academicYear", "2024").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/curriculum-courses").param("academicYear", "2024").param("departmentEntityId", other.getEntityId().toString()).with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].entityId").value(general.toString()));
    }

    @Test void sameNamesWithDifferentCodesAreFilteredByYearAndRplCannotBypassIt() throws Exception {
        var second = courses.saveAndFlush(new Course(departments.findByEntityIdAndDeletedAtIsNull(department).orElseThrow(), "PLAN102", "기초", new BigDecimal("3.0"), CourseCategory.MAJOR)).getEntityId();
        var old = create(input(null, course));
        var newer = create(input(null, second).replace("2024", "2025"));
        for (var row : new tools.jackson.databind.JsonNode[]{old, newer}) {
            mvc.perform(put("/api/v1/admin/curricula/" + row.path("entityId").asText() + "/publication")
                .with(jwt().jwt(t -> t.subject(admin.toString()))).contentType("application/json")
                .content("{\"published\":true,\"version\":0}")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/curriculum-courses").param("academicYear", "2024").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].code").value("PLAN101"));
        mvc.perform(get("/api/v1/curriculum-courses").param("academicYear", "2025").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].code").value("PLAN102"));
        jdbc.update("INSERT INTO student(auth_user_id, email, admission_year, enrollment_status) VALUES (?, ?, 2024, 'ENROLLED')", admin, admin + "@inha.edu");
        mvc.perform(post("/api/v1/grades").with(jwt().jwt(t -> t.subject(admin.toString())))
            .contentType("application/json").content(gradeInput(2025).replace("\"A_PLUS\"", "null").replace("\"rpl\":false", "\"rpl\":true")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_GRADE"));
        mvc.perform(get("/api/v1/curriculum-courses").param("academicYear", "1999").with(jwt()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private tools.jackson.databind.JsonNode create(String input) throws Exception {
        var result = mvc.perform(post("/api/v1/admin/curricula").with(jwt().jwt(t -> t.subject(admin.toString())))
            .contentType("application/json").content(input)).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String input(Long version, UUID courseId) {
        return """
            {"departmentEntityId":"%s","curriculumYear":2024,"sourceTitle":"공식 자료","version":%s,
             "courses":[{"courseEntityId":"%s","division":"MAJOR_REQUIRED"}]}
            """.formatted(department, version, courseId);
    }
}
