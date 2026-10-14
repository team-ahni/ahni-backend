package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record ScholarshipResultResponse(
    @Schema(description = "장학 기준 식별자", example = "00000000-0000-0000-0000-000000000501")
    UUID criteriaEntityId,
    @Schema(description = "장학금 이름", example = "성적우수장학금")
    String name,
    @Schema(description = "최소 평점", example = "3.50")
    BigDecimal minGpa,
    @Schema(description = "최소 이수학점", example = "12.0")
    BigDecimal minCredits,
    @Schema(description = "허용 학사경고 횟수", example = "0")
    int maxWarnings,
    @Schema(description = "기준까지 부족한 평점, 충족 시 0", example = "0.30")
    BigDecimal gpaShortfall,
    @Schema(description = "기준까지 부족한 이수학점, 충족 시 0", example = "0.0")
    BigDecimal creditShortfall,
    @Schema(description = "허용 횟수를 초과한 학사경고 횟수, 충족 시 0", example = "0")
    int excessWarnings,
    @Schema(description = "모든 기준 충족 여부", example = "false")
    boolean met
) { }
