package com.ahni.backend.dto;

import com.ahni.backend.domain.AcademicTerm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;

public record LectureRequest(
    @NotNull UUID courseEntityId,
    @NotBlank @Size(max = 500) @Schema(example = "남춘성,이우형", description = "학교 자료의 담당교수 표기. 공동강의도 하나의 문자열로 보존합니다.") String professorName,
    @Min(2000) @Max(9999) @Schema(example = "2026") int academicYear,
    @NotNull AcademicTerm term,
    @NotBlank @Size(max = 30) @Schema(example = "001") String section,
    @NotNull @Size(min = 1, max = 14) List<@NotNull @Valid LectureScheduleRequest> schedules
) { }
