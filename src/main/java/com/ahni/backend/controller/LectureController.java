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
import com.ahni.backend.service.LectureService;

@RestController
@RequestMapping("/api/v1/lectures")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 요청값 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청값이 올바르지 않습니다.\"}"))),
    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
    @ApiResponse(responseCode = "404", description = "학생, 강의 또는 시간표 항목 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"TIMETABLE_NOT_FOUND\",\"message\":\"시간표 항목을 찾을 수 없습니다.\"}"))),
    @ApiResponse(responseCode = "409", description = "TIMETABLE_CONFLICT: 중복 또는 수업 시간 충돌", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"TIMETABLE_CONFLICT\",\"message\":\"이미 시간표에 등록한 강의입니다.\"}")))
})
public class LectureController {
    private final LectureService service;
    private final com.ahni.backend.service.CurriculumService curricula;
    public LectureController(LectureService service, com.ahni.backend.service.CurriculumService curricula) { this.service = service; this.curricula = curricula; }

    @GetMapping("/courses")
    @Operation(summary = "학기별 개설 과목 조회", description = "선택한 연도와 학기의 개설 강의를 과목별로 중복 제거하고 해당 분반의 교수명을 반환합니다.")
    public List<OfferedCourseResponse> courses(@RequestParam @jakarta.validation.constraints.Min(2000) @jakarta.validation.constraints.Max(9999) int academicYear, @RequestParam AcademicTerm term) {
        return curricula.getOfferedCourses(academicYear, term);
    }

    @GetMapping
    @Operation(summary = "개설 강의 조회", description = "연도·학기의 실제 개설 강의를 조회합니다. 교과과정 공개 여부와는 별개입니다.")
    public List<LectureResponse> list(@RequestParam int academicYear, @RequestParam AcademicTerm term) {
        return service.list(academicYear, term);
    }
}
