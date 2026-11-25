package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GraduationRequirementResponse(
    @Schema(
        description = "졸업요건 식별자",
        example = "00000000-0000-0000-0000-000000000401"
    )
    UUID entityId,
    @Schema(description = "적용 입학연도", example = "2024")
    Integer admissionYear,
    @Schema(
        description = "적용 전공 유형",
        example = "PRIMARY",
        allowableValues = {"PRIMARY", "DOUBLE_MAJOR", "MINOR"}
    )
    String majorType,
    @Schema(description = "적용 학과")
    DepartmentResponse department,
    @Schema(description = "최소 총 이수학점", example = "130.0")
    BigDecimal minTotalCredit,
    @Schema(description = "해당 전공 유형의 최소 이수학점", example = "60.0")
    BigDecimal minDepartmentCredit,
    @Schema(description = "최소 교양 이수학점", example = "30.0")
    BigDecimal minGeneralCredit,
    @Schema(description = "공식 졸업요건 자료명", example = "2024학년도 졸업요건")
    String sourceTitle,
    @Schema(
        description = "공식 졸업요건 원문 URL",
        example = "https://example.edu/requirements/2024",
        nullable = true
    )
    String sourceUrl,
    @Schema(description = "활성 필수과목 목록")
    List<RequiredCourseResponse> requiredCourses,
    @Schema(description = "전공 65학점과 별도로 이수하는 전공기초 최소학점", example = "15.0") BigDecimal minFoundationCredit,
    @Schema(description = "선택·대체·경과조치를 포함한 전체 자동 판정이 지원되는지. false면 충족 판정을 확정하지 않습니다.") boolean evaluationComplete
) {
    public GraduationRequirementResponse(UUID entityId, Integer admissionYear, String majorType, DepartmentResponse department,
        BigDecimal minTotalCredit, BigDecimal minDepartmentCredit, BigDecimal minGeneralCredit,
        String sourceTitle, String sourceUrl, List<RequiredCourseResponse> requiredCourses) {
        this(entityId, admissionYear, majorType, department, minTotalCredit, minDepartmentCredit, minGeneralCredit,
            sourceTitle, sourceUrl, requiredCourses, BigDecimal.ZERO, true);
    }
}
