package com.ahni.backend.controller;

import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.entity.Student;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:notice_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class NoticeIntegrationTest {
    private static final String ADMIN_NOTICES = "/api/v1/admin/notices";
    private static final String STUDENT_NOTICES = "/api/v1/notices";
    private static final String DRAFT = """
        {"title":"수강신청 안내","body":"일정을 확인해 주세요."}
        """;

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void draftCanBePublishedAndHiddenByAnActiveAdministrator() throws Exception {
        UUID adminId = UUID.randomUUID();
        jdbc.update("""
            insert into admin (entity_id, auth_user_id, name, email, created_at, updated_at)
            values (?, ?, '관리자', 'notice-admin@inha.edu', current_timestamp, current_timestamp)
            """, UUID.randomUUID(), adminId);
        UUID studentId = UUID.randomUUID();
        entityManager.persist(new Student(studentId, "notice-student@inha.edu", 2024,
            EnrollmentStatus.ENROLLED, null));
        entityManager.flush();

        var admin = jwt().jwt(token -> token.subject(adminId.toString()));
        var student = jwt().jwt(token -> token.subject(studentId.toString()));
        mvc.perform(get(ADMIN_NOTICES).with(student)).andExpect(status().isForbidden());
        mvc.perform(get(STUDENT_NOTICES)).andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN_NOTICES).with(student).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
            .andExpect(status().isForbidden());

        var created = mvc.perform(post(ADMIN_NOTICES).with(admin).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.publishedAt").isEmpty())
            .andReturn().getResponse().getContentAsString();
        String entityId = json.readTree(created).path("entityId").asText();
        mvc.perform(get(STUDENT_NOTICES).with(student)).andExpect(jsonPath("$").isEmpty());

        mvc.perform(put(ADMIN_NOTICES + "/{id}", entityId).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content(update(true, 0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.publishedAt").isString());
        entityManager.clear();
        mvc.perform(get(STUDENT_NOTICES).with(student)).andExpect(jsonPath("$[0].entityId").value(entityId));

        mvc.perform(put(ADMIN_NOTICES + "/{id}", entityId).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content(update(false, 0)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NOTICE_VERSION_CONFLICT"));
        mvc.perform(put(ADMIN_NOTICES + "/{id}", entityId).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content(update(false, 1)))
            .andExpect(status().isOk());
        entityManager.clear();
        mvc.perform(get(STUDENT_NOTICES).with(student)).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void softDeletesPublishedNoticeAndPreservesRegistrationInformation() throws Exception {
        UUID adminId = UUID.randomUUID();
        jdbc.update("""
            insert into admin (entity_id, auth_user_id, name, email, created_at, updated_at)
            values (?, ?, '최초 등록 관리자', 'notice-deletion@inha.edu', current_timestamp, current_timestamp)
            """, UUID.randomUUID(), adminId);
        UUID studentId = UUID.randomUUID();
        entityManager.persist(new Student(studentId, "notice-deletion-student@inha.edu", 2024,
            EnrollmentStatus.ENROLLED, null));
        entityManager.flush();

        var admin = jwt().jwt(token -> token.subject(adminId.toString()));
        var student = jwt().jwt(token -> token.subject(studentId.toString()));
        var created = mvc.perform(post(ADMIN_NOTICES).with(admin).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdByName").value("최초 등록 관리자"))
            .andExpect(jsonPath("$.createdAt").isString())
            .andReturn().getResponse().getContentAsString();
        var draft = json.readTree(created);
        String id = draft.path("entityId").asText();
        String noticePath = ADMIN_NOTICES + "/{id}";
        mvc.perform(put(noticePath, id).with(admin).contentType(MediaType.APPLICATION_JSON).content(update(true, 0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdAt").value(draft.path("createdAt").asText()))
            .andExpect(jsonPath("$.createdByName").value("최초 등록 관리자"));

        mvc.perform(delete(noticePath, id).param("version", "1").with(student))
            .andExpect(status().isForbidden());
        mvc.perform(delete(noticePath, id).param("version", "0").with(admin))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NOTICE_VERSION_CONFLICT"));
        mvc.perform(get(STUDENT_NOTICES).with(student)).andExpect(jsonPath("$[0].entityId").value(id));

        mvc.perform(delete(noticePath, id).param("version", "1").with(admin))
            .andExpect(status().isNoContent());
        mvc.perform(get(ADMIN_NOTICES).with(admin)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(STUDENT_NOTICES).with(student)).andExpect(jsonPath("$").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from notice where entity_id = ? and deleted_at is not null",
            Long.class, UUID.fromString(id))).isEqualTo(1);
        mvc.perform(put(noticePath, id).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content(update(true, 2)))
            .andExpect(status().isNotFound());
    }

    private static String update(boolean published, int version) {
        return """
            {"title":"수강신청 안내","body":"일정을 확인해 주세요.","published":%b,"version":%d}
            """.formatted(published, version);
    }
}
