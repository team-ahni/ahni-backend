package com.ahni.backend.dto;

import com.ahni.backend.domain.CourseRecommendationReason;
import com.ahni.backend.domain.CurriculumDivision;
import com.ahni.backend.domain.GradeCode;
import com.ahni.backend.domain.RecommendedTerm;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RecommendedCourseResponse(
    @Schema(description = "추천 과목") CourseResponse course,
    @Schema(description = "교과과정 구분") CurriculumDivision division,
    @Schema(description = "권장 학년. 없으면 null", nullable = true, example = "2") Integer recommendedYear,
    @Schema(description = "권장 학기. 없으면 null", nullable = true) RecommendedTerm recommendedTerm,
    @Schema(description = "추천 이유. 우선순위가 높은 순서입니다.") List<CourseRecommendationReason> reasons,
    @Schema(description = "재수강 추천일 때 가장 최근 성적. 아니면 null", nullable = true) GradeCode previousGradeCode,
    @Schema(description = "해당 학기 개설 분반. 개설 정보가 없는 학기면 빈 배열") List<LectureResponse> lectures
) { }
