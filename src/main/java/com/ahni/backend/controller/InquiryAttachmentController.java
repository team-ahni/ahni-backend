package com.ahni.backend.controller;

import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.dto.InquiryAttachmentFile;
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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inquiries/{inquiryEntityId}/attachments")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
    @ApiResponse(responseCode = "404", description = "학생 프로필, 문의 또는 첨부파일을 찾을 수 없음. 다른 학생의 문의도 404로 응답",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
            examples = {
                @ExampleObject(name = "inquiry", value = "{\"code\":\"INQUIRY_NOT_FOUND\",\"message\":\"문의를 찾을 수 없습니다.\"}"),
                @ExampleObject(name = "attachment", value = "{\"code\":\"INQUIRY_ATTACHMENT_NOT_FOUND\",\"message\":\"첨부파일을 찾을 수 없습니다.\"}")
            })),
    @ApiResponse(responseCode = "503", description = "첨부파일 저장소 오류",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"INQUIRY_ATTACHMENT_STORAGE_UNAVAILABLE\",\"message\":\"첨부파일 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.\"}")))
})
public class InquiryAttachmentController {
    private final InquiryAttachmentService service;

    public InquiryAttachmentController(InquiryAttachmentService service) {
        this.service = service;
    }

    @Operation(
        summary = "내 문의 첨부파일 등록",
        description = "[인증 O] 답변 전 문의에 JPG, PNG, PDF 파일을 5MB 이하로 최대 3개까지 첨부합니다. "
            + "파일 확장자, 파트의 Content-Type, 파일 내용이 모두 같은 형식이어야 합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "첨부파일 등록 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InquiryAttachmentResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "entityId":"00000000-0000-0000-0000-000000000801",
                      "filename":"grade-screen.png",
                      "contentType":"image/png",
                      "sizeBytes":182044,
                      "createdAt":"2026-10-17T00:00:00Z"
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "빈 파일, 파일명 오류 또는 file 파트 누락",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"INVALID_INQUIRY_ATTACHMENT\",\"message\":\"빈 파일은 첨부할 수 없습니다.\"}"))),
        @ApiResponse(responseCode = "409", description = "답변된 문의이거나 첨부 개수 초과",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "answered", value = "{\"code\":\"INQUIRY_UPDATE_CONFLICT\",\"message\":\"답변이 등록된 문의는 수정할 수 없습니다.\"}"),
                    @ExampleObject(name = "limit", value = "{\"code\":\"INQUIRY_ATTACHMENT_LIMIT_EXCEEDED\",\"message\":\"문의 하나에 첨부파일은 3개까지 등록할 수 있습니다.\"}")
                })),
        @ApiResponse(responseCode = "413", description = "5MB 초과",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"INQUIRY_ATTACHMENT_TOO_LARGE\",\"message\":\"첨부파일은 5MB 이하만 등록할 수 있습니다.\"}"))),
        @ApiResponse(responseCode = "415", description = "허용하지 않는 파일 형식",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"UNSUPPORTED_INQUIRY_ATTACHMENT_TYPE\",\"message\":\"JPG, PNG, PDF 파일만 첨부할 수 있습니다.\"}")))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public InquiryAttachmentResponse upload(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId,
        @RequestPart("file") MultipartFile file
    ) throws IOException {
        return service.upload(
            UUID.fromString(jwt.getSubject()),
            inquiryEntityId,
            file.getOriginalFilename(),
            file.getContentType(),
            file.getBytes()
        );
    }

    @Operation(summary = "내 문의 첨부파일 목록", description = "[인증 O] 본인 문의의 첨부파일을 등록순으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "첨부파일 목록 조회 성공",
        content = @Content(mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = InquiryAttachmentResponse.class))))
    @GetMapping
    public List<InquiryAttachmentResponse> getMine(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId
    ) {
        return service.getMine(UUID.fromString(jwt.getSubject()), inquiryEntityId);
    }

    @Operation(summary = "내 문의 첨부파일 다운로드", description = "[인증 O] 본인 문의의 첨부파일을 내려받습니다. 항상 attachment로 응답합니다.")
    @ApiResponse(responseCode = "200", description = "첨부파일 내용",
        content = {
            @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))
        })
    @GetMapping("/{attachmentEntityId}")
    public ResponseEntity<byte[]> download(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId,
        @PathVariable UUID attachmentEntityId
    ) {
        return fileResponse(service.downloadMine(
            UUID.fromString(jwt.getSubject()),
            inquiryEntityId,
            attachmentEntityId
        ));
    }

    @Operation(summary = "내 문의 첨부파일 삭제", description = "[인증 O] 답변 전 문의의 첨부파일을 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "첨부파일 삭제 성공", content = @Content),
        @ApiResponse(responseCode = "409", description = "답변된 문의",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"INQUIRY_UPDATE_CONFLICT\",\"message\":\"답변이 등록된 문의는 수정할 수 없습니다.\"}")))
    })
    @DeleteMapping("/{attachmentEntityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId,
        @PathVariable UUID attachmentEntityId
    ) {
        service.delete(UUID.fromString(jwt.getSubject()), inquiryEntityId, attachmentEntityId);
    }

    static ResponseEntity<byte[]> fileResponse(InquiryAttachmentFile file) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .body(file.content());
    }
}
