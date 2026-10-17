package com.ahni.backend.controller;

import com.ahni.backend.config.SecurityConfiguration;
import com.ahni.backend.dto.InquiryAttachmentFile;
import com.ahni.backend.dto.InquiryAttachmentResponse;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.service.InquiryAttachmentService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminInquiryAttachmentController.class)
@Import(SecurityConfiguration.class)
@Tag("integration")
class AdminInquiryAttachmentControllerTest {
    private static final UUID AUTH_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID INQUIRY_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID ATTACHMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final String BASE = "/api/v1/admin/inquiries/" + INQUIRY_ID + "/attachments";
    private static final byte[] PDF = "%PDF-1.7".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InquiryAttachmentService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void 관리자가_첨부파일_목록을_조회한다() throws Exception {
        when(service.getForAdmin(AUTH_USER_ID, INQUIRY_ID)).thenReturn(List.of(new InquiryAttachmentResponse(
            ATTACHMENT_ID, "report.pdf", "application/pdf", PDF.length, Instant.parse("2026-10-17T00:00:00Z")
        )));

        mockMvc.perform(get(BASE).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].entityId").value(ATTACHMENT_ID.toString()));
    }

    @Test
    void 관리자가_첨부파일을_내려받는다() throws Exception {
        when(service.downloadForAdmin(AUTH_USER_ID, INQUIRY_ID, ATTACHMENT_ID))
            .thenReturn(new InquiryAttachmentFile("report.pdf", "application/pdf", PDF));

        mockMvc.perform(get(BASE + "/" + ATTACHMENT_ID).with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"report.pdf\"; filename*=UTF-8''report.pdf"));
    }

    @Test
    void 관리자가_아니면_403을_응답한다() throws Exception {
        when(service.downloadForAdmin(AUTH_USER_ID, INQUIRY_ID, ATTACHMENT_ID))
            .thenThrow(new AdminAccessDeniedException());

        mockMvc.perform(get(BASE + "/" + ATTACHMENT_ID).with(admin()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()));
    }
}
