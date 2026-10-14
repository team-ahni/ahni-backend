package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record ScholarshipEligibilityResponse(
    @Schema(description = "적용 입학연도", example = "2024")
    int admissionYear,
    @Schema(description = "적용 학과(주전공)")
    DepartmentResponse department,
    @Schema(description = "누적 GPA", example = "3.20")
    BigDecimal gpa,
    @Schema(description = "누적 이수학점", example = "42.0")
    BigDecimal completedCredits,
    @Schema(description = "정규학기 평점 1.70 미만 학사경고 횟수", example = "0")
    int warningCount,
    @Schema(description = "장학금별 판정 결과. 비어 있으면 등록된 기준이 없습니다.")
    List<ScholarshipResultResponse> scholarships
) { }
