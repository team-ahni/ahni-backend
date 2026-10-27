package com.ahni.backend.controller;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.dto.CourseRecommendationResponse;
import com.ahni.backend.service.CourseRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/course-recommendations")
@SecurityRequirement(name = "bearerAuth")
public class CourseRecommendationController {
    private final CourseRecommendationService service;

    public CourseRecommendationController(CourseRecommendationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
        summary = "수강 추천",
        description = "[인증 O] 본인 전공의 공개 교과과정, 졸업 필수 과목, 이수 성적, 개설 강의로 정규학기 수강 과목을 추천합니다. "
            + "졸업 필수 미이수, 권장 학기 지남, 이번 학기 권장, 재수강(C+ 이하·F·NP) 순서로 정렬합니다. "
            + "추천은 안내이며 수강 신청 자격을 보장하지 않습니다."
    )
    @ApiResponse(responseCode = "200", description = "추천 조회 성공",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CourseRecommendationResponse.class)))
    @ApiResponse(responseCode = "400", description = "연도 범위 오류 또는 계절학기 요청",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청값이 올바르지 않습니다.\"}")))
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    @ApiResponse(responseCode = "404", description = "학생 프로필 없음",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"STUDENT_NOT_FOUND\",\"message\":\"학생 프로필을 찾을 수 없습니다.\"}")))
    public CourseRecommendationResponse recommend(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam int academicYear,
        @RequestParam AcademicTerm term
    ) {
        return service.recommend(UUID.fromString(jwt.getSubject()), academicYear, term);
    }
}
