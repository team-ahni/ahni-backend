package com.ahni.backend.controller;

import com.ahni.backend.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import com.ahni.backend.service.LectureService;

@RestController
@RequestMapping("/api/v1/admin")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 요청값 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청값이 올바르지 않습니다.\"}"))),
    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
    @ApiResponse(responseCode = "404", description = "학생, 강의 또는 시간표 항목 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"TIMETABLE_NOT_FOUND\",\"message\":\"시간표 항목을 찾을 수 없습니다.\"}"))),
    @ApiResponse(responseCode = "409", description = "TIMETABLE_CONFLICT: 중복 또는 수업 시간 충돌", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"TIMETABLE_CONFLICT\",\"message\":\"이미 시간표에 등록한 강의입니다.\"}")))
})
@ApiResponse(responseCode = "403", description = "활성 관리자 프로필 필요",
    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
        examples = @ExampleObject(value = "{\"code\":\"ADMIN_ACCESS_DENIED\",\"message\":\"관리자 권한이 필요합니다.\"}")))
public class AdminLectureController {
    private final LectureService service;
    public AdminLectureController(LectureService service) { this.service = service; }

    @PostMapping(value = "/lectures/import", produces = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "개설 강의 일괄 등록", description = "같은 학기의 1~200강의를 원자적으로 등록합니다. 하나라도 실패하면 전체를 취소합니다. 검수된 UUID와 시간 입력을 받으며 엑셀 자체는 저장하지 않습니다.")
    @ApiResponse(responseCode = "201", description = "전체 등록 성공",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = LectureResponse.class))))
    public List<LectureResponse> importLectures(@AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody LectureImportRequest request) {
        return service.importLectures(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/lectures")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "개설 강의 등록", description = "기존 활성 과목과 담당교수 이름을 저장하며 요일별 시간을 1~14건 입력합니다.")
    @ApiResponse(responseCode = "201", description = "강의 등록 성공",
        content = @Content(schema = @Schema(implementation = LectureResponse.class)))
    public LectureResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LectureRequest request) {
        return service.create(UUID.fromString(jwt.getSubject()), request);
    }
}
