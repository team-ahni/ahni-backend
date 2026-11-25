package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record GraduationCreditProgressResponse(
    @Schema(description = "전체 학점 충족도")
    CreditProgressResponse total,
    @Schema(description = "해당 학과의 공개 교과과정에 수강연도별로 연결된 전공 학점 충족도")
    CreditProgressResponse department,
    @Schema(description = "교양 학점 충족도")
    CreditProgressResponse general,
    @Schema(description = "전공과 중복 합산하지 않는 별도 전공기초 학점 충족도") CreditProgressResponse foundation
) {
    public GraduationCreditProgressResponse(CreditProgressResponse total, CreditProgressResponse department, CreditProgressResponse general) {
        this(total, department, general, new CreditProgressResponse(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, true));
    }
}
