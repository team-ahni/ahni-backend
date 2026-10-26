package com.ahni.backend.controller;

import com.ahni.backend.config.SecurityConfiguration;
import com.ahni.backend.dto.AdminIdentityResponse;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.service.AdminAccessService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAccessController.class)
@Import(SecurityConfiguration.class)
@Tag("integration")
class AdminAccessControllerTest {
    private static final UUID AUTH_USER_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000901"
    );
    private static final UUID ADMIN_ENTITY_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000902"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccessService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void 인증된_관리자는_본인_관리자_정보를_조회한다() throws Exception {
        when(service.getCurrentAdmin(AUTH_USER_ID)).thenReturn(
            new AdminIdentityResponse(
                ADMIN_ENTITY_ID,
                "관리자 이름",
                "admin@inha.edu"
            )
        );

        mockMvc.perform(get("/api/v1/admin/me")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entityId").value(ADMIN_ENTITY_ID.toString()))
            .andExpect(jsonPath("$.name").value("관리자 이름"))
            .andExpect(jsonPath("$.email").value("admin@inha.edu"));
    }

    @Test
    void 인증되지_않은_요청은_관리자_정보를_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 활성_관리자가_아니면_403과_안정적인_오류_코드를_반환한다() throws Exception {
        when(service.getCurrentAdmin(AUTH_USER_ID))
            .thenThrow(new AdminAccessDeniedException());

        mockMvc.perform(get("/api/v1/admin/me")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void 관리자는_활성_관리자_목록을_조회한다() throws Exception {
        when(service.findActiveAdmins(AUTH_USER_ID)).thenReturn(List.of(
            new AdminIdentityResponse(ADMIN_ENTITY_ID, "관리자 이름", "admin@inha.edu")
        ));

        mockMvc.perform(get("/api/v1/admin/admins")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].entityId").value(ADMIN_ENTITY_ID.toString()))
            .andExpect(jsonPath("$[0].name").value("관리자 이름"));
    }

    @Test
    void 관리자가_아니면_관리자_목록을_조회할_수_없다() throws Exception {
        when(service.findActiveAdmins(AUTH_USER_ID)).thenThrow(new AdminAccessDeniedException());

        mockMvc.perform(get("/api/v1/admin/admins")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }
}
