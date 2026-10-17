package com.ahni.backend.controller;

import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.dto.InquiryAttachmentResponse;
import com.ahni.backend.service.InquiryAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/inquiries/{inquiryEntityId}/attachments")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"ADMIN_ACCESS_DENIED\",\"message\":\"관리자 권한이 필요합니다.\"}"))),
    @ApiResponse(responseCode = "404", description = "문의 또는 첨부파일 없음",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
            examples = {
                @ExampleObject(name = "inquiry", value = "{\"code\":\"INQUIRY_NOT_FOUND\",\"message\":\"문의를 찾을 수 없습니다.\"}"),
                @ExampleObject(name = "attachment", value = "{\"code\":\"INQUIRY_ATTACHMENT_NOT_FOUND\",\"message\":\"첨부파일을 찾을 수 없습니다.\"}")
            })),
    @ApiResponse(responseCode = "503", description = "첨부파일 저장소 오류",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"INQUIRY_ATTACHMENT_STORAGE_UNAVAILABLE\",\"message\":\"첨부파일 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.\"}")))
})
public class AdminInquiryAttachmentController {
    private final InquiryAttachmentService service;

    public AdminInquiryAttachmentController(InquiryAttachmentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "관리자 문의 첨부파일 목록", description = "[관리자 인증 O] 학생이 삭제한 문의를 포함해 첨부파일을 등록순으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "첨부파일 목록 조회 성공",
        content = @Content(mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = InquiryAttachmentResponse.class))))
    public List<InquiryAttachmentResponse> findAll(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId
    ) {
        return service.getForAdmin(UUID.fromString(jwt.getSubject()), inquiryEntityId);
    }

    @GetMapping("/{attachmentEntityId}")
    @Operation(summary = "관리자 문의 첨부파일 다운로드", description = "[관리자 인증 O] 첨부파일을 내려받습니다. 항상 attachment로 응답합니다.")
    @ApiResponse(responseCode = "200", description = "첨부파일 내용",
        content = {
            @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))
        })
    public ResponseEntity<byte[]> download(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId,
        @PathVariable UUID attachmentEntityId
    ) {
        return InquiryAttachmentController.fileResponse(service.downloadForAdmin(
            UUID.fromString(jwt.getSubject()),
            inquiryEntityId,
            attachmentEntityId
        ));
    }
}
