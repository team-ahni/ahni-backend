package com.ahni.backend.dto;

import com.ahni.backend.domain.CourseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OfferedCourseResponse(
    UUID entityId, String code, String name, BigDecimal credit,
    CourseCategory category, @Schema(nullable = true) DepartmentResponse department,
    @Schema(description = "해당 학기 개설 분반의 교수명 목록. 선택한 담당교수를 의미하지 않습니다.") List<String> professorNames
) { }
