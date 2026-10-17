package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.UUID;

public record LectureScheduleResponse(UUID entityId, int dayOfWeek,
    @Schema(type = "string", example = "09:00") LocalTime startTime,
    @Schema(type = "string", example = "10:30") LocalTime endTime,
    @Schema(nullable = true) String classroom) { }
