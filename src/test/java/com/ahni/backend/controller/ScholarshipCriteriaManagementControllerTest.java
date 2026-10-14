package com.ahni.backend.controller;

import com.ahni.backend.config.SecurityConfiguration;
import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.ScholarshipCriteriaResponse;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.exception.ScholarshipCriteriaAlreadyExistsException;
import com.ahni.backend.exception.ScholarshipCriteriaNotFoundException;
import com.ahni.backend.service.ScholarshipCriteriaManagementService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScholarshipCriteriaManagementController.class)
@Import(SecurityConfiguration.class)
@Tag("integration")
class ScholarshipCriteriaManagementControllerTest {
    private static final UUID ADMIN_AUTH_USER_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000901"
    );
    private static final UUID DEPARTMENT_ENTITY_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID CRITERIA_ENTITY_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000501"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScholarshipCriteriaManagementService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void 필터에_맞는_장학_기준_목록을_조회한다() throws Exception {
        when(service.findAll(ADMIN_AUTH_USER_ID, DEPARTMENT_ENTITY_ID, 2024))
            .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/admin/scholarship-criteria")
                .with(adminJwt())
                .queryParam("departmentEntityId", DEPARTMENT_ENTITY_ID.toString())
                .queryParam("admissionYear", "2024"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].entityId").value(CRITERIA_ENTITY_ID.toString()))
            .andExpect(jsonPath("$[0].department.name").value("소프트웨어융합공학과"));
    }

    @Test
    void 장학_기준을_등록한다() throws Exception {
        when(service.create(eq(ADMIN_AUTH_USER_ID), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/admin/scholarship-criteria")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("성적우수장학금"))
            .andExpect(jsonPath("$.minGpa").value(3.50));
    }

    @Test
    void 최소_평점이_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/scholarship-criteria")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest().replace("3.50", "4.60")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 중복된_장학_기준은_409를_반환한다() throws Exception {
        when(service.create(eq(ADMIN_AUTH_USER_ID), any()))
            .thenThrow(new ScholarshipCriteriaAlreadyExistsException());

        mockMvc.perform(post("/api/v1/admin/scholarship-criteria")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SCHOLARSHIP_CRITERIA_ALREADY_EXISTS"));
    }

    @Test
    void 활성_관리자가_아니면_403을_반환한다() throws Exception {
        when(service.create(eq(ADMIN_AUTH_USER_ID), any()))
            .thenThrow(new AdminAccessDeniedException());

        mockMvc.perform(post("/api/v1/admin/scholarship-criteria")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void 인증되지_않은_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/scholarship-criteria"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 장학_기준을_수정한다() throws Exception {
        when(service.update(eq(ADMIN_AUTH_USER_ID), eq(CRITERIA_ENTITY_ID), any()))
            .thenReturn(response());

        mockMvc.perform(put("/api/v1/admin/scholarship-criteria/{id}", CRITERIA_ENTITY_ID)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"성적우수장학금","minGpa":3.50,"minCredits":12.0,"maxWarnings":0}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxWarnings").value(0));
    }

    @Test
    void 장학_기준을_삭제한다() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/scholarship-criteria/{id}", CRITERIA_ENTITY_ID)
                .with(adminJwt()))
            .andExpect(status().isNoContent());

        verify(service).delete(ADMIN_AUTH_USER_ID, CRITERIA_ENTITY_ID);
    }

    @Test
    void 없는_장학_기준을_삭제하면_404를_반환한다() throws Exception {
        doThrow(new ScholarshipCriteriaNotFoundException())
            .when(service).delete(ADMIN_AUTH_USER_ID, CRITERIA_ENTITY_ID);

        mockMvc.perform(delete("/api/v1/admin/scholarship-criteria/{id}", CRITERIA_ENTITY_ID)
                .with(adminJwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SCHOLARSHIP_CRITERIA_NOT_FOUND"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.subject(ADMIN_AUTH_USER_ID.toString()));
    }

    private String createRequest() {
        return """
            {
              "departmentEntityId": "00000000-0000-0000-0000-000000000001",
              "admissionYear": 2024,
              "name": "성적우수장학금",
              "minGpa": 3.50,
              "minCredits": 12.0,
              "maxWarnings": 0
            }
            """;
    }

    private ScholarshipCriteriaResponse response() {
        return new ScholarshipCriteriaResponse(
            CRITERIA_ENTITY_ID,
            new DepartmentResponse(DEPARTMENT_ENTITY_ID, "소프트웨어융합공학과"),
            2024,
            "성적우수장학금",
            new BigDecimal("3.50"),
            new BigDecimal("12.0"),
            0
        );
    }
}
