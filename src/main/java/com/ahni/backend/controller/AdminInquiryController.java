package com.ahni.backend.controller;

import com.ahni.backend.dto.AdminInquiryResponse;
import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.dto.InquiryAnswerRequest;
import com.ahni.backend.dto.InquiryAssigneeRequest;
import com.ahni.backend.service.AdminInquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/inquiries")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"ADMIN_ACCESS_DENIED\",\"message\":\"관리자 권한이 필요합니다.\"}"))),
    @ApiResponse(responseCode = "404", description = "문의 없음",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"INQUIRY_NOT_FOUND\",\"message\":\"문의를 찾을 수 없습니다.\"}")))
})
public class AdminInquiryController {
    private final AdminInquiryService service;

    public AdminInquiryController(AdminInquiryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "관리자 문의 목록", description = "[관리자 인증 O] 학생이 삭제한 문의까지 포함해 최신순으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "문의 목록 조회 성공",
        content = @Content(mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = AdminInquiryResponse.class))))
    public List<AdminInquiryResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        return service.findAll(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{inquiryEntityId}")
    @Operation(summary = "관리자 문의 상세", description = "[관리자 인증 O] 문의 상세를 조회하고 접수 상태면 확인 중으로 표시합니다.")
    @ApiResponse(responseCode = "200", description = "문의 상세 조회 성공",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminInquiryResponse.class)))
    public AdminInquiryResponse get(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId
    ) {
        return service.get(UUID.fromString(jwt.getSubject()), inquiryEntityId);
    }

    @PutMapping("/{inquiryEntityId}/answer")
    @Operation(summary = "문의 답변 등록", description = "[관리자 인증 O] 문의에 답변을 등록하고 상태를 답변 완료로 변경합니다.")
    @ApiResponse(responseCode = "200", description = "문의 답변 등록 성공",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminInquiryResponse.class)))
    @ApiResponse(responseCode = "400", description = "답변 입력 오류",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청값이 올바르지 않습니다.\"}")))
    public AdminInquiryResponse answer(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId,
        @Valid @RequestBody InquiryAnswerRequest request
    ) {
        return service.answer(UUID.fromString(jwt.getSubject()), inquiryEntityId, request);
    }

    @PutMapping("/{inquiryEntityId}/assignee")
    @Operation(summary = "문의 담당자 지정", description = "[관리자 인증 O] 활성 관리자를 문의 담당자로 지정합니다. adminEntityId가 null이면 담당자를 해제합니다. 담당자 지정은 답변 권한을 제한하지 않습니다.")
    @ApiResponse(responseCode = "200", description = "문의 담당자 지정 성공",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminInquiryResponse.class)))
    @ApiResponse(responseCode = "400", description = "없는 관리자 또는 삭제된 문의",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"INVALID_INQUIRY\",\"message\":\"담당 관리자를 찾을 수 없습니다.\"}")))
    public AdminInquiryResponse assign(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId,
        @RequestBody InquiryAssigneeRequest request
    ) {
        return service.assign(UUID.fromString(jwt.getSubject()), inquiryEntityId, request);
    }
}
