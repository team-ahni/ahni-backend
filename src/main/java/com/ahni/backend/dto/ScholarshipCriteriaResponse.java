package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record ScholarshipCriteriaResponse(
    @Schema(description = "장학 기준 식별자", example = "00000000-0000-0000-0000-000000000501")
    UUID entityId,
    @Schema(description = "적용 학과")
    DepartmentResponse department,
    @Schema(description = "적용 입학연도", example = "2024")
    int admissionYear,
    @Schema(description = "장학금 이름", example = "성적우수장학금")
    String name,
    @Schema(description = "최소 평점", example = "3.50")
    BigDecimal minGpa,
    @Schema(description = "최소 이수학점", example = "12.0")
    BigDecimal minCredits,
    @Schema(description = "허용 학사경고 횟수", example = "0")
    int maxWarnings
) { }
