package com.ahni.backend.controller;

import com.ahni.backend.config.SecurityConfiguration;
import com.ahni.backend.domain.InquiryStatus;
import com.ahni.backend.dto.AdminIdentityResponse;
import com.ahni.backend.dto.AdminInquiryResponse;
import com.ahni.backend.dto.AdminInquiryStudentResponse;
import com.ahni.backend.dto.InquiryAnswerRequest;
import com.ahni.backend.dto.InquiryAssigneeRequest;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.exception.InquiryNotFoundException;
import com.ahni.backend.exception.InvalidInquiryException;
import com.ahni.backend.service.AdminInquiryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminInquiryController.class)
@Import(SecurityConfiguration.class)
@Tag("integration")
class AdminInquiryControllerTest {
    private static final UUID AUTH_USER_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000010"
    );
    private static final UUID ASSIGNEE_ENTITY_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000011"
    );
    private static final UUID INQUIRY_ENTITY_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000701"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminInquiryService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void 관리자가_문의_목록을_조회한다() throws Exception {
        when(service.findAll(AUTH_USER_ID))
            .thenReturn(List.of(response(InquiryStatus.SUBMITTED, null, null, null)));

        mockMvc.perform(get("/api/v1/admin/inquiries")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].entityId").value(INQUIRY_ENTITY_ID.toString()))
            .andExpect(jsonPath("$[0].student.email").value("student@inha.edu"))
            .andExpect(jsonPath("$[0].status").value("SUBMITTED"));

        verify(service).findAll(AUTH_USER_ID);
    }

    @Test
    void 관리자가_문의_상세를_조회한다() throws Exception {
        when(service.get(AUTH_USER_ID, INQUIRY_ENTITY_ID))
            .thenReturn(response(InquiryStatus.IN_REVIEW, null, null, null));

        mockMvc.perform(get("/api/v1/admin/inquiries/" + INQUIRY_ENTITY_ID)
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_REVIEW"))
            .andExpect(jsonPath("$.content").value("2025년 과목이 보이지 않습니다."));

        verify(service).get(AUTH_USER_ID, INQUIRY_ENTITY_ID);
    }

    @Test
    void 관리자가_문의에_답변한다() throws Exception {
        InquiryAnswerRequest request = new InquiryAnswerRequest("확인했습니다.");
        when(service.answer(AUTH_USER_ID, INQUIRY_ENTITY_ID, request))
            .thenReturn(response(
                InquiryStatus.ANSWERED,
                "확인했습니다.",
                "관리자",
                Instant.parse("2026-10-09T01:00:00Z")
            ));

        mockMvc.perform(put("/api/v1/admin/inquiries/" + INQUIRY_ENTITY_ID + "/answer")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"answer": "확인했습니다."}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ANSWERED"))
            .andExpect(jsonPath("$.answer").value("확인했습니다."))
            .andExpect(jsonPath("$.answeredByAdminName").value("관리자"));

        verify(service).answer(AUTH_USER_ID, INQUIRY_ENTITY_ID, request);
    }

    @Test
    void 빈_답변은_400을_응답한다() throws Exception {
        mockMvc.perform(put("/api/v1/admin/inquiries/" + INQUIRY_ENTITY_ID + "/answer")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"answer": " "}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void 관리자가_문의_담당자를_지정한다() throws Exception {
        InquiryAssigneeRequest request = new InquiryAssigneeRequest(ASSIGNEE_ENTITY_ID);
        when(service.assign(AUTH_USER_ID, INQUIRY_ENTITY_ID, request))
            .thenReturn(response(
                InquiryStatus.IN_REVIEW,
                new AdminIdentityResponse(ASSIGNEE_ENTITY_ID, "담당자", "assignee@inha.edu"),
                null,
                null,
                null
            ));

        mockMvc.perform(put("/api/v1/admin/inquiries/" + INQUIRY_ENTITY_ID + "/assignee")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"adminEntityId": "00000000-0000-0000-0000-000000000011"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignedAdmin.entityId").value(ASSIGNEE_ENTITY_ID.toString()))
            .andExpect(jsonPath("$.assignedAdmin.name").value("담당자"));

        verify(service).assign(AUTH_USER_ID, INQUIRY_ENTITY_ID, request);
    }

    @Test
    void 없는_관리자를_담당자로_지정하면_400을_응답한다() throws Exception {
        InquiryAssigneeRequest request = new InquiryAssigneeRequest(ASSIGNEE_ENTITY_ID);
        when(service.assign(AUTH_USER_ID, INQUIRY_ENTITY_ID, request))
            .thenThrow(new InvalidInquiryException("담당 관리자를 찾을 수 없습니다."));

        mockMvc.perform(put("/api/v1/admin/inquiries/" + INQUIRY_ENTITY_ID + "/assignee")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"adminEntityId": "00000000-0000-0000-0000-000000000011"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INQUIRY"));
    }

    @Test
    void 관리자_권한이_없으면_403을_응답한다() throws Exception {
        when(service.findAll(AUTH_USER_ID)).thenThrow(new AdminAccessDeniedException());

        mockMvc.perform(get("/api/v1/admin/inquiries")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void 문의가_없으면_404를_응답한다() throws Exception {
        when(service.get(AUTH_USER_ID, INQUIRY_ENTITY_ID))
            .thenThrow(new InquiryNotFoundException());

        mockMvc.perform(get("/api/v1/admin/inquiries/" + INQUIRY_ENTITY_ID)
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
    }

    @Test
    void 인증되지_않은_사용자는_관리자_문의에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    private static AdminInquiryResponse response(
        InquiryStatus status,
        String answer,
        String answeredByAdminName,
        Instant answeredAt
    ) {
        return response(status, null, answer, answeredByAdminName, answeredAt);
    }

    private static AdminInquiryResponse response(
        InquiryStatus status,
        AdminIdentityResponse assignedAdmin,
        String answer,
        String answeredByAdminName,
        Instant answeredAt
    ) {
        return new AdminInquiryResponse(
            INQUIRY_ENTITY_ID,
            new AdminInquiryStudentResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000020"),
                "student@inha.edu",
                "인하"
            ),
            "성적 등록 문의",
            "2025년 과목이 보이지 않습니다.",
            status,
            assignedAdmin,
            answer,
            answeredByAdminName,
            answeredAt,
            Instant.parse("2026-10-09T00:00:00Z"),
            Instant.parse("2026-10-09T00:00:00Z"),
            null
        );
    }
}
