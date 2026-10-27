package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MajorCourseRecommendationResponse(
    @Schema(description = "전공 유형", allowableValues = {"PRIMARY", "DOUBLE_MAJOR", "MINOR"}) String majorType,
    @Schema(description = "전공 학과") DepartmentResponse department,
    @Schema(description = "해당 연도 교과과정이 공개돼 있는지. false면 courses는 빈 배열", example = "true") boolean curriculumAvailable,
    @Schema(description = "추천 과목. 앞 전공에서 추천한 과목은 다시 넣지 않습니다.") List<RecommendedCourseResponse> courses
) { }
