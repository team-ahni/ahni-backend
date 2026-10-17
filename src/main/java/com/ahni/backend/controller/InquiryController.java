package com.ahni.backend.controller;

import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.dto.InquiryCreateRequest;
import com.ahni.backend.dto.InquiryResponse;
import com.ahni.backend.service.InquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inquiries")
public class InquiryController {
    private final InquiryService inquiryService;

    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @Operation(
        summary = "내 문의 등록",
        description = "[인증 O] 학생이 관리자에게 문의를 남깁니다. 첨부파일은 등록 후 첨부파일 API로 추가합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "문의 등록 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InquiryResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "entityId":"00000000-0000-0000-0000-000000000701",
                      "title":"성적 등록 문의",
                      "content":"2025년 과목이 성적 등록 화면에 보이지 않습니다.",
                      "status":"SUBMITTED",
                      "answer":null,
                      "answeredAt":null,
                      "createdAt":"2026-10-09T00:00:00Z",
                      "updatedAt":"2026-10-09T00:00:00Z"
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "요청값 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청값이 올바르지 않습니다.\"}"))),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "404", description = "학생 프로필을 찾을 수 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"STUDENT_NOT_FOUND\",\"message\":\"학생 프로필을 찾을 수 없습니다.\"}")))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InquiryResponse create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody InquiryCreateRequest request
    ) {
        return inquiryService.create(UUID.fromString(jwt.getSubject()), request);
    }

    @Operation(
        summary = "내 문의 목록 조회",
        description = "[인증 O] 본인이 남긴 문의를 최신순으로 조회합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "문의 목록 조회 성공",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = InquiryResponse.class)))),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "404", description = "학생 프로필을 찾을 수 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public List<InquiryResponse> getMine(@AuthenticationPrincipal Jwt jwt) {
        return inquiryService.getMine(UUID.fromString(jwt.getSubject()));
    }

    @Operation(
        summary = "내 문의 상세 조회",
        description = "[인증 O] 본인이 남긴 문의 하나를 조회합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "문의 상세 조회 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InquiryResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "404", description = "학생 프로필 또는 문의를 찾을 수 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"INQUIRY_NOT_FOUND\",\"message\":\"문의를 찾을 수 없습니다.\"}")))
    })
    @GetMapping("/{inquiryEntityId}")
    public InquiryResponse getMine(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId
    ) {
        return inquiryService.getMine(UUID.fromString(jwt.getSubject()), inquiryEntityId);
    }

    @Operation(
        summary = "내 문의 수정",
        description = "[인증 O] 답변 전 문의의 제목과 내용을 수정합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "문의 수정 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InquiryResponse.class))),
        @ApiResponse(responseCode = "400", description = "요청값 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "404", description = "학생 프로필 또는 문의를 찾을 수 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "이미 답변된 문의",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"INQUIRY_UPDATE_CONFLICT\",\"message\":\"답변이 등록된 문의는 수정할 수 없습니다.\"}")))
    })
    @PutMapping("/{inquiryEntityId}")
    public InquiryResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId,
        @Valid @RequestBody InquiryCreateRequest request
    ) {
        return inquiryService.update(
            UUID.fromString(jwt.getSubject()),
            inquiryEntityId,
            request
        );
    }

    @Operation(
        summary = "내 문의 삭제",
        description = "[인증 O] 문의를 학생 화면에서 숨깁니다. 관리자 이력은 보존합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "문의 삭제 성공", content = @Content),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "404", description = "학생 프로필 또는 문의를 찾을 수 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{inquiryEntityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID inquiryEntityId
    ) {
        inquiryService.delete(UUID.fromString(jwt.getSubject()), inquiryEntityId);
    }
}
