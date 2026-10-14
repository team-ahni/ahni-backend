package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ScholarshipCriteriaUpdateRequest(
    @NotBlank
    @Size(max = 100)
    @Schema(description = "장학금 이름", example = "성적우수장학금")
    String name,
    @NotNull @DecimalMin("0.00") @DecimalMax("4.50") @Digits(integer = 1, fraction = 2)
    @Schema(description = "최소 평점", example = "3.50")
    BigDecimal minGpa,
    @NotNull @DecimalMin("0.0") @Digits(integer = 3, fraction = 1)
    @Schema(description = "최소 이수학점", example = "12.0")
    BigDecimal minCredits,
    @Min(0)
    @Max(99)
    @Schema(description = "허용 학사경고 횟수", example = "0")
    int maxWarnings
) { }
