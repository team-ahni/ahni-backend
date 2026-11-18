package com.ahni.backend.controller;

import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.dto.NoticeResponse;
import com.ahni.backend.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class NoticeController {
    private final NoticeService notices;

    public NoticeController(NoticeService notices) {
        this.notices = notices;
    }

    @Operation(summary = "관리자 공지 목록 조회")
    @ApiResponse(responseCode = "200", description = "공지 목록 조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음 (ADMIN_ACCESS_DENIED)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping(value = "/api/v1/admin/notices", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<NoticeResponse> listAdminNotices(@AuthenticationPrincipal Jwt jwt) {
        return notices.listAdmin(UUID.fromString(jwt.getSubject()));
    }

    @Operation(summary = "학생 공개 공지 목록 조회")
    @ApiResponse(responseCode = "200", description = "공개 공지 목록 조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    @ApiResponse(responseCode = "404", description = "학생 프로필 없음 (STUDENT_NOT_FOUND)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping(value = "/api/v1/notices", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<NoticeResponse> listPublishedNotices(@AuthenticationPrincipal Jwt jwt) {
        return notices.listPublished(UUID.fromString(jwt.getSubject()));
    }

    @Operation(summary = "공지 초안 등록")
    @ApiResponse(responseCode = "201", description = "공지 초안 등록 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음 (ADMIN_ACCESS_DENIED)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (INVALID_REQUEST)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(value = "/api/v1/admin/notices", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public NoticeResponse createNotice(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NoticeCreateRequest request) {
        return notices.create(UUID.fromString(jwt.getSubject()), request.title(), request.body());
    }

    @Operation(summary = "공지 수정 및 공개 설정")
    @ApiResponse(responseCode = "200", description = "공지 수정 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음 (ADMIN_ACCESS_DENIED)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (INVALID_REQUEST)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "공지 없음 (NOTICE_NOT_FOUND)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class),
        examples = @ExampleObject(value = """
            {"code":"NOTICE_NOT_FOUND","message":"공지를 찾을 수 없습니다."}
            """)))
    @ApiResponse(responseCode = "409", description = "동시 수정 (NOTICE_VERSION_CONFLICT)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class),
        examples = @ExampleObject(value = """
            {"code":"NOTICE_VERSION_CONFLICT","message":"공지가 변경되었습니다. 목록을 새로고침해 주세요."}
            """)))
    @PutMapping(value = "/api/v1/admin/notices/{noticeId}", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public NoticeResponse updateNotice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID noticeId,
        @Valid @RequestBody NoticeUpdateRequest request) {
        return notices.update(UUID.fromString(jwt.getSubject()), noticeId, request.title(), request.body(),
            request.published(), request.version());
    }

    @Operation(summary = "공지 삭제", description = "삭제 시간을 기록하고 공지를 조회에서 숨깁니다.")
    @ApiResponse(responseCode = "204", description = "공지 삭제 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    @ApiResponse(responseCode = "403", description = "ADMIN_ACCESS_DENIED")
    @ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "NOTICE_VERSION_CONFLICT")
    @DeleteMapping("/api/v1/admin/notices/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNotice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID noticeId,
        @RequestParam long version) {
        notices.delete(UUID.fromString(jwt.getSubject()), noticeId, version);
    }

    public record NoticeCreateRequest(
        @NotBlank @Size(max = 150) @Schema(example = "수강신청 안내") String title,
        @NotBlank @Size(max = 10000) @Schema(example = "수강신청 일정을 확인해 주세요.") String body
    ) {}

    public record NoticeUpdateRequest(
        @NotBlank @Size(max = 150) @Schema(example = "수강신청 안내") String title,
        @NotBlank @Size(max = 10000) @Schema(example = "수강신청 일정을 확인해 주세요.") String body,
        @NotNull Boolean published,
        @NotNull @PositiveOrZero Long version
    ) {}
}
