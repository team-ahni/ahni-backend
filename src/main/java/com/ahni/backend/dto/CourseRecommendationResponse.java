package com.ahni.backend.dto;

import com.ahni.backend.domain.AcademicTerm;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CourseRecommendationResponse(
    @Schema(description = "추천 대상 연도", example = "2026") int academicYear,
    @Schema(description = "추천 대상 학기") AcademicTerm term,
    @Schema(description = "추천 대상 학기가 몇 번째 정규학기인지. 이전 정규학기 성적 수 + 1", example = "5") int semesterNumber,
    @Schema(description = "해당 학기 개설 강의 정보가 있는지. false면 개설 여부로 거르지 않습니다.", example = "true") boolean lectureDataAvailable,
    @Schema(description = "전공별 추천. 주전공, 복수전공, 부전공 순서") List<MajorCourseRecommendationResponse> majors
) { }
