package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GraduationRequirementCreateRequest(
    @NotNull
    @Schema(
        description = "적용 학과 식별자",
        example = "00000000-0000-0000-0000-000000000001"
    )
    UUID departmentEntityId,
    @Min(2000)
    @Max(9999)
    @Schema(description = "적용 입학연도", example = "2024")
    int admissionYear,
    @NotBlank
    @Pattern(regexp = "PRIMARY|DOUBLE_MAJOR|MINOR")
    @Schema(
        description = "적용 전공 유형",
        example = "PRIMARY",
        allowableValues = {"PRIMARY", "DOUBLE_MAJOR", "MINOR"}
    )
    String majorType,
    @NotNull @DecimalMin("0.0") @Digits(integer = 3, fraction = 1)
    @Schema(description = "최소 총 이수학점", example = "130.0")
    BigDecimal minTotalCredit,
    @NotNull @DecimalMin("0.0") @Digits(integer = 3, fraction = 1)
    @Schema(description = "해당 전공 유형의 최소 이수학점", example = "60.0")
    BigDecimal minDepartmentCredit,
    @NotNull @DecimalMin("0.0") @Digits(integer = 3, fraction = 1)
    @Schema(description = "최소 교양 이수학점", example = "30.0")
    BigDecimal minGeneralCredit,
    @NotBlank
    @Size(max = 200)
    @Schema(description = "공식 졸업요건 자료명", example = "2024학년도 졸업요건")
    String sourceTitle,
    @URL
    @Size(max = 2048)
    @Schema(
        description = "공식 졸업요건 원문 URL",
        example = "https://example.edu/requirements/2024",
        nullable = true
    )
    String sourceUrl,
    @NotNull
    List<@Valid RequiredCourseAssignmentRequest> requiredCourses,
    @DecimalMin("0.0") @Digits(integer = 3, fraction = 1)
    @Schema(description = "전공 학점과 별도로 요구되는 전공기초 학점. 등록 시 생략하면 0, 수정 시 생략하면 기존 값 유지", example = "15.0", nullable = true)
    BigDecimal minFoundationCredit
) {

    public GraduationRequirementCreateRequest(UUID departmentEntityId, int admissionYear, String majorType,
        BigDecimal minTotalCredit, BigDecimal minDepartmentCredit, BigDecimal minGeneralCredit,
        String sourceTitle, String sourceUrl, List<RequiredCourseAssignmentRequest> requiredCourses) {
        this(departmentEntityId, admissionYear, majorType, minTotalCredit, minDepartmentCredit, minGeneralCredit,
            sourceTitle, sourceUrl, requiredCourses, null);
    }
}
