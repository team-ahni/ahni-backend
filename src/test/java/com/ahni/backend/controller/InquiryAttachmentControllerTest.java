package com.ahni.backend.controller;

import com.ahni.backend.config.SecurityConfiguration;
import com.ahni.backend.dto.InquiryAttachmentFile;
import com.ahni.backend.dto.InquiryAttachmentResponse;
import com.ahni.backend.exception.InquiryAttachmentLimitExceededException;
import com.ahni.backend.exception.InquiryAttachmentNotFoundException;
import com.ahni.backend.exception.InquiryAttachmentStorageUnavailableException;
import com.ahni.backend.exception.InquiryAttachmentTooLargeException;
import com.ahni.backend.exception.UnsupportedInquiryAttachmentTypeException;
import com.ahni.backend.service.InquiryAttachmentService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InquiryAttachmentController.class)
@Import(SecurityConfiguration.class)
@Tag("integration")
class InquiryAttachmentControllerTest {
    private static final UUID AUTH_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID INQUIRY_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID ATTACHMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final String BASE = "/api/v1/inquiries/" + INQUIRY_ID + "/attachments";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InquiryAttachmentService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void 인증된_학생이_파일을_첨부한다() throws Exception {
        when(service.upload(AUTH_USER_ID, INQUIRY_ID, "grade.png", "image/png", PNG))
            .thenReturn(response());

        mockMvc.perform(multipart(BASE)
                .file(new MockMultipartFile("file", "grade.png", "image/png", PNG))
                .with(student()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.entityId").value(ATTACHMENT_ID.toString()))
            .andExpect(jsonPath("$.contentType").value("image/png"))
            .andExpect(jsonPath("$.sizeBytes").value(PNG.length));
    }

    @Test
    void file_파트가_없으면_400을_응답한다() throws Exception {
        mockMvc.perform(multipart(BASE)
                .file(new MockMultipartFile("other", "grade.png", "image/png", PNG))
                .with(student()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void 검증_실패를_고정_에러_코드로_응답한다() throws Exception {
        when(service.upload(eq(AUTH_USER_ID), eq(INQUIRY_ID), any(), any(), any()))
            .thenThrow(new UnsupportedInquiryAttachmentTypeException())
            .thenThrow(new InquiryAttachmentTooLargeException())
            .thenThrow(new InquiryAttachmentLimitExceededException())
            .thenThrow(new InquiryAttachmentStorageUnavailableException());
        MockMultipartFile file = new MockMultipartFile("file", "grade.svg", "image/svg+xml", PNG);

        mockMvc.perform(multipart(BASE).file(file).with(student()))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_INQUIRY_ATTACHMENT_TYPE"));
        mockMvc.perform(multipart(BASE).file(file).with(student()))
            .andExpect(status().isContentTooLarge())
            .andExpect(jsonPath("$.code").value("INQUIRY_ATTACHMENT_TOO_LARGE"));
        mockMvc.perform(multipart(BASE).file(file).with(student()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INQUIRY_ATTACHMENT_LIMIT_EXCEEDED"));
        mockMvc.perform(multipart(BASE).file(file).with(student()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("INQUIRY_ATTACHMENT_STORAGE_UNAVAILABLE"));
    }

    @Test
    void 첨부파일_목록을_조회한다() throws Exception {
        when(service.getMine(AUTH_USER_ID, INQUIRY_ID)).thenReturn(List.of(response()));

        mockMvc.perform(get(BASE).with(student()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].filename").value("grade.png"));
    }

    @Test
    void 다운로드는_항상_attachment와_nosniff로_응답한다() throws Exception {
        when(service.downloadMine(AUTH_USER_ID, INQUIRY_ID, ATTACHMENT_ID))
            .thenReturn(new InquiryAttachmentFile("성적 화면.png", "image/png", PNG));

        mockMvc.perform(get(BASE + "/" + ATTACHMENT_ID).with(student()))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(PNG))
            .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
            .andExpect(header().string("Content-Disposition",
                endsWith("filename*=UTF-8''%EC%84%B1%EC%A0%81%20%ED%99%94%EB%A9%B4.png")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void 찾을_수_없는_첨부파일은_404를_응답한다() throws Exception {
        when(service.downloadMine(AUTH_USER_ID, INQUIRY_ID, ATTACHMENT_ID))
            .thenThrow(new InquiryAttachmentNotFoundException());

        mockMvc.perform(get(BASE + "/" + ATTACHMENT_ID).with(student()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INQUIRY_ATTACHMENT_NOT_FOUND"));
    }

    @Test
    void 첨부파일을_삭제한다() throws Exception {
        mockMvc.perform(delete(BASE + "/" + ATTACHMENT_ID).with(student()))
            .andExpect(status().isNoContent());

        verify(service).delete(AUTH_USER_ID, INQUIRY_ID, ATTACHMENT_ID);
    }

    @Test
    void 인증되지_않은_사용자는_접근할_수_없다() throws Exception {
        mockMvc.perform(multipart(BASE).file(new MockMultipartFile("file", "grade.png", "image/png", PNG)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/" + ATTACHMENT_ID))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor student() {
        return jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()));
    }

    private static InquiryAttachmentResponse response() {
        return new InquiryAttachmentResponse(
            ATTACHMENT_ID, "grade.png", "image/png", PNG.length, Instant.parse("2026-10-17T00:00:00Z")
        );
    }
}
