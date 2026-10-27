package com.ahni.backend.dto;

import com.ahni.backend.domain.AcademicTerm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record LectureUpdateRequest(
    @NotNull UUID courseEntityId,
    @NotBlank @Size(max = 500) String professorName,
    @Min(2000) @Max(9999) int academicYear,
    @NotNull AcademicTerm term,
    @NotBlank @Size(max = 30) String section,
    @NotNull @Size(max = 14)
    @Schema(description = "수업 시간 전체 교체. 고정시간 없는 강좌는 빈 배열입니다.")
    List<@NotNull @Valid LectureScheduleRequest> schedules
) { }
