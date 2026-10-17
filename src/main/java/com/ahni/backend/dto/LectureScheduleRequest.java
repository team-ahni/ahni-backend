package com.ahni.backend.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

public record LectureScheduleRequest(
    @Min(1) @Max(7) @Schema(description = "월요일 1부터 일요일 7", example = "1") int dayOfWeek,
    @NotNull @Schema(type = "string", example = "09:00") LocalTime startTime,
    @NotNull @Schema(type = "string", example = "10:30") LocalTime endTime,
    @Size(max = 200) @Schema(nullable = true, example = "하이테크관 101호") String classroom
) { }
