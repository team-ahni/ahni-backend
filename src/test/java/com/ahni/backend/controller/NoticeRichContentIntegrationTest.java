package com.ahni.backend.controller;

import com.ahni.backend.client.NoticeImageStorageClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:notice_rich;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Tag("integration")
class NoticeRichContentIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private NoticeImageStorageClient storage;
    private UUID admin;
    private UUID student;
    private static final byte[] PNG = {(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    @BeforeEach
    void setup() {
        jdbc.update("delete from notice_image");
        jdbc.update("delete from notice");
        admin = UUID.randomUUID();
        student = UUID.randomUUID();
        jdbc.update("""
            insert into admin(entity_id,auth_user_id,name,email,created_at,updated_at)
            values(?,?,'공지 관리자',?,current_timestamp,current_timestamp)
            """, UUID.randomUUID(), admin, admin + "@inha.edu");
        jdbc.update("""
            insert into student(entity_id,auth_user_id,email,admission_year,enrollment_status,account_status,created_at,updated_at)
            values(?,?,?,2024,'ENROLLED','ACTIVE',current_timestamp,current_timestamp)
            """, UUID.randomUUID(), student, student + "@inha.edu");
    }

    @Test
    void savesOrderedContentAndEnforcesImageVisibilityAndOwnership() throws Exception {
        UUID imageId = UUID.randomUUID();
        String created = mvc.perform(multipart("/api/v1/admin/notices")
            .file(part("notice", payload(imageId)))
            .file(part("imageIds", "[\"" + imageId + "\"]"))
            .file(new MockMultipartFile("images", "photo.png", "image/png", PNG))
            .with(jwt().jwt(token -> token.subject(admin.toString()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.body").value("앞 문단\n\n뒤 문단"))
            .andExpect(jsonPath("$.bodyJson.content[1].attrs.imageId").value(imageId.toString()))
            .andReturn().getResponse().getContentAsString();
        var notice = json.readTree(created);
        String id = notice.path("entityId").asText();
        int version = notice.path("version").asInt();
        String imagePath = "/api/v1/notices/" + id + "/images/" + imageId;
        mvc.perform(get(imagePath).with(jwt().jwt(token -> token.subject(student.toString()))))
            .andExpect(status().isNotFound());
        when(storage.download(anyString())).thenReturn(PNG);
        mvc.perform(get("/api/v1/admin/notices/" + id + "/images/" + imageId)
                .with(jwt().jwt(token -> token.subject(admin.toString()))))
            .andExpect(status().isOk()).andExpect(content().bytes(PNG));
        // Visibility-only requests from older clients preserve rich content.
        var publish = json.createObjectNode().put("title", "사진 공지").put("body", notice.path("body").asText())
            .put("published", true).put("version", version);
        mvc.perform(put("/api/v1/admin/notices/" + id).with(jwt().jwt(token -> token.subject(admin.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(publish.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.bodyJson.content[1].type").value("image"));
        mvc.perform(get(imagePath).with(jwt().jwt(token -> token.subject(student.toString()))))
            .andExpect(status().isOk()).andExpect(content().bytes(PNG)).andExpect(header().string("Cache-Control", "no-store"));

        // An existing photo can only be referenced by its own notice.
        mvc.perform(post("/api/v1/admin/notices").with(jwt().jwt(token -> token.subject(admin.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(payload(imageId)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_NOTICE_CONTENT"));
        assertThat(jdbc.queryForObject("select count(*) from notice", Integer.class)).isEqualTo(1);

        // Removing a photo from the document also revokes access to the old image endpoint.
        String textOnly = """
            {"title":"사진 공지","body":"새 내용","published":true,"version":%d,
             "bodyJson":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"새 내용"}]}]}}
            """.formatted(version + 1);
        mvc.perform(put("/api/v1/admin/notices/" + id).with(jwt().jwt(token -> token.subject(admin.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(textOnly))
            .andExpect(status().isOk());
        mvc.perform(get(imagePath).with(jwt().jwt(token -> token.subject(student.toString()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void rollsBackNoticeAndStorageWhenAnImageUploadFails() throws Exception {
        doThrow(com.ahni.backend.exception.NoticeException.storageUnavailable())
            .when(storage).upload(anyString(), anyString(), any());
        UUID imageId = UUID.randomUUID();
        mvc.perform(multipart("/api/v1/admin/notices").file(part("notice", payload(imageId)))
                .file(part("imageIds", "[\"" + imageId + "\"]"))
                .file(new MockMultipartFile("images", "photo.png", "image/png", PNG))
                .with(jwt().jwt(token -> token.subject(admin.toString()))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("NOTICE_IMAGE_STORAGE_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("select count(*) from notice", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notice_image", Integer.class)).isZero();
        verify(storage).delete(anyString());
    }

    private static MockMultipartFile part(String name, String text) {
        return new MockMultipartFile(name, "", "application/json", text.getBytes(StandardCharsets.UTF_8));
    }

    private static String payload(UUID imageId) {
        return """
            {"title":"사진 공지","body":"","bodyJson":{"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"앞 문단","marks":[{"type":"bold"}]}]},
              {"type":"image","attrs":{"imageId":"%s","alt":"안내 사진"}},
              {"type":"paragraph","content":[{"type":"text","text":"뒤 문단"}]}
            ]}}
            """.formatted(imageId);
    }
}
