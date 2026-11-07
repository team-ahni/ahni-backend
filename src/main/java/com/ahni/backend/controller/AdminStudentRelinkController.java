package com.ahni.backend.controller;

import com.ahni.backend.dto.AdminStudentRelinkRequestResponse;
import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.service.StudentRelinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/student-relink-requests")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"ADMIN_ACCESS_DENIED\",\"message\":\"관리자 권한이 필요합니다.\"}")))
})
public class AdminStudentRelinkController {
    private static final String RESOLVED_EXAMPLE = """
        {
          "entityId": "00000000-0000-0000-0000-000000000030",
          "email": "student@inha.edu",
          "status": "APPROVED",
          "createdAt": "2026-11-07T01:00:00Z",
          "resolvedAt": "2026-11-07T02:00:00Z",
          "resolvedBy": {
            "entityId": "00000000-0000-0000-0000-000000000040",
            "name": "관리자",
            "email": "admin@inha.ac.kr"
          },
          "student": {
            "studentEntityId": "00000000-0000-0000-0000-000000000020",
            "email": "student@inha.edu",
            "nickname": "인하",
            "primaryDepartment": {
              "entityId": "00000000-0000-0000-0000-000000000001",
              "name": "소프트웨어융합공학과"
            },
            "doubleMajorDepartment": null,
            "minorDepartment": null,
            "admissionYear": 2024,
            "enrollmentStatus": "ENROLLED",
            "accountStatus": "ACTIVE"
          },
          "studentCreatedAt": "2025-03-02T00:00:00Z"
        }
        """;

    private final StudentRelinkService service;

    public AdminStudentRelinkController(StudentRelinkService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "학생 계정 재연결 요청 목록",
        description = "[관리자 인증 O] 처리된 요청까지 최신순으로 조회합니다. 기존 프로필 정보는 본인 확인에 사용합니다.")
    @ApiResponse(responseCode = "200", description = "재연결 요청 목록 조회 성공",
        content = @Content(mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = AdminStudentRelinkRequestResponse.class))))
    public List<AdminStudentRelinkRequestResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        return service.findAll(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/{requestEntityId}/approve")
    @Operation(summary = "학생 계정 재연결 승인",
        description = "[관리자 인증 O] 본인 확인을 마친 요청을 승인해 기존 학생 프로필을 새 계정에 연결합니다. "
            + "이전 계정 식별자와 처리 관리자를 기록하고, 같은 프로필의 다른 대기 요청은 거절합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "재연결 승인 성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = AdminStudentRelinkRequestResponse.class),
                examples = @ExampleObject(value = RESOLVED_EXAMPLE))),
        @ApiResponse(responseCode = "404", description = "재연결 요청 없음",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"STUDENT_RELINK_REQUEST_NOT_FOUND\",\"message\":\"계정 재연결 요청을 찾을 수 없습니다.\"}"))),
        @ApiResponse(responseCode = "409", description = "이미 처리됐거나 연결할 수 없는 요청",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "이미 처리됨", value = "{\"code\":\"STUDENT_RELINK_CONFLICT\",\"message\":\"이미 처리된 재연결 요청입니다.\"}"),
                    @ExampleObject(name = "새 계정 사용 중", value = "{\"code\":\"STUDENT_RELINK_CONFLICT\",\"message\":\"새 계정에 이미 다른 학생 프로필이 연결되어 있습니다.\"}"),
                    @ExampleObject(name = "프로필 변경", value = "{\"code\":\"STUDENT_RELINK_CONFLICT\",\"message\":\"요청 이후 기존 학생 프로필이 변경되어 연결할 수 없습니다.\"}")
                }))
    })
    public AdminStudentRelinkRequestResponse approve(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID requestEntityId
    ) {
        return service.approve(UUID.fromString(jwt.getSubject()), requestEntityId);
    }

    @PostMapping("/{requestEntityId}/reject")
    @Operation(summary = "학생 계정 재연결 거절",
        description = "[관리자 인증 O] 본인 확인에 실패한 요청을 거절합니다. 학생 프로필은 바뀌지 않습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "재연결 거절 성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = AdminStudentRelinkRequestResponse.class))),
        @ApiResponse(responseCode = "404", description = "재연결 요청 없음",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"STUDENT_RELINK_REQUEST_NOT_FOUND\",\"message\":\"계정 재연결 요청을 찾을 수 없습니다.\"}"))),
        @ApiResponse(responseCode = "409", description = "이미 처리된 요청",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"STUDENT_RELINK_CONFLICT\",\"message\":\"이미 처리된 재연결 요청입니다.\"}")))
    })
    public AdminStudentRelinkRequestResponse reject(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID requestEntityId
    ) {
        return service.reject(UUID.fromString(jwt.getSubject()), requestEntityId);
    }
}
