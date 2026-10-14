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

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.service.TimetableService;

@RestController
@RequestMapping("/api/v1/timetable")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 요청값 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청값이 올바르지 않습니다.\"}"))),
    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
    @ApiResponse(responseCode = "404", description = "학생, 강의 또는 시간표 항목 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"TIMETABLE_NOT_FOUND\",\"message\":\"시간표 항목을 찾을 수 없습니다.\"}"))),
    @ApiResponse(responseCode = "409", description = "TIMETABLE_CONFLICT: 중복 또는 수업 시간 충돌", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"TIMETABLE_CONFLICT\",\"message\":\"이미 시간표에 등록한 강의입니다.\"}")))
})
public class TimetableController {
    private final TimetableService service;
    public TimetableController(TimetableService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "내 학기 시간표 조회", description = "본인 학생 프로필에 연결된 강의만 조회합니다.")
    public List<TimetableResponse> list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam int academicYear, @RequestParam AcademicTerm term) {
        return service.list(UUID.fromString(jwt.getSubject()), academicYear, term);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "내 시간표에 강의 추가", description = "중복 또는 같은 학기·요일의 시간 충돌은 409로 거부합니다.")
    @ApiResponse(responseCode = "201", description = "등록 성공",
        content = @Content(schema = @Schema(implementation = TimetableResponse.class)))
    public TimetableResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TimetableRequest request) {
        return service.create(UUID.fromString(jwt.getSubject()), request);
    }

    @PutMapping("/{entityId}")
    @Operation(summary = "내 시간표 강의 변경", description = "같은 학기의 다른 강의로 교체하고 충돌을 재검사합니다.")
    public TimetableResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
        @Valid @RequestBody TimetableRequest request) {
        return service.update(UUID.fromString(jwt.getSubject()), entityId, request);
    }

    @DeleteMapping("/{entityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "내 시간표 강의 삭제", description = "본인의 등록만 삭제하며 개설 강의 정보는 유지합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공", content = @Content)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId) {
        service.delete(UUID.fromString(jwt.getSubject()), entityId);
    }
}
