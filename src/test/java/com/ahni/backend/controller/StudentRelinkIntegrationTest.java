package com.ahni.backend.controller;

import com.ahni.backend.entity.Department;
import com.ahni.backend.repository.DepartmentRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class StudentRelinkIntegrationTest {
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
    UUID admin;
    UUID oldAccount;
    UUID newAccount;

    @BeforeEach void seed() throws Exception {
        admin = UUID.randomUUID();
        oldAccount = UUID.randomUUID();
        newAccount = UUID.randomUUID();
        jdbc.update("INSERT INTO admin(auth_user_id, name, email) VALUES (?, '관리자', ?)", admin, admin + "@inha.ac.kr");
        UUID department = departments.saveAndFlush(new Department("재연결학과")).getEntityId();
        mvc.perform(post("/api/v1/students/me").with(student(oldAccount)).contentType("application/json")
                .content("""
                    {"primaryDepartmentEntityId":"%s","admissionYear":2024,"enrollmentStatus":"ENROLLED","nickname":"인하"}
                    """.formatted(department)))
            .andExpect(status().isCreated());
    }

    @Test void recreatedAccountReclaimsProfileOnlyAfterAdministratorApproval() throws Exception {
        mvc.perform(post("/api/v1/students/me").with(student(newAccount)).contentType("application/json")
                .content("{\"primaryDepartmentEntityId\":\"" + UUID.randomUUID() + "\",\"admissionYear\":2024,\"enrollmentStatus\":\"ENROLLED\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STUDENT_EMAIL_ALREADY_REGISTERED"));

        String requestId = json.readTree(mvc.perform(post("/api/v1/students/me/relink-requests").with(student(newAccount)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn().getResponse().getContentAsString()).path("entityId").asText();
        mvc.perform(post("/api/v1/students/me/relink-requests").with(student(newAccount)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.entityId").value(requestId));
        mvc.perform(get("/api/v1/students/me").with(student(newAccount))).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/admin/student-relink-requests").with(student(newAccount)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
        mvc.perform(post("/api/v1/admin/student-relink-requests/" + requestId + "/approve").with(student(newAccount)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/student-relink-requests").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].entityId").value(requestId))
            .andExpect(jsonPath("$[0].student.nickname").value("인하"))
            .andExpect(jsonPath("$[0].student.primaryDepartment.name").value("재연결학과"));

        mvc.perform(post("/api/v1/admin/student-relink-requests/" + requestId + "/approve").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.resolvedBy.name").value("관리자"));
        assertThat(jdbc.queryForObject("SELECT previous_auth_user_id FROM student_relink_request WHERE entity_id = ?::uuid",
            UUID.class, requestId)).isEqualTo(oldAccount);
        mvc.perform(get("/api/v1/students/me").with(student(newAccount)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("인하"));
        mvc.perform(get("/api/v1/students/me").with(student(oldAccount))).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/admin/student-relink-requests/" + requestId + "/reject").with(admin()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STUDENT_RELINK_CONFLICT"));
        mvc.perform(post("/api/v1/admin/student-relink-requests/" + UUID.randomUUID() + "/approve").with(admin()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STUDENT_RELINK_REQUEST_NOT_FOUND"));
    }

    @Test void requestNeedsAnExistingProfileWithTheSameEmail() throws Exception {
        mvc.perform(post("/api/v1/students/me/relink-requests")
                .with(jwt().jwt(t -> t.subject(newAccount.toString()).claim("email", "nobody@inha.edu"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
        mvc.perform(post("/api/v1/students/me/relink-requests").with(student(oldAccount)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STUDENT_ALREADY_REGISTERED"));
        mvc.perform(post("/api/v1/students/me/relink-requests")).andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor student(UUID account) {
        return jwt().jwt(t -> t.subject(account.toString()).claim("email", "relink@inha.edu"));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(t -> t.subject(admin.toString()));
    }
}
