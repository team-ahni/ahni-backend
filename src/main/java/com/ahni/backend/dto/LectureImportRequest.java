package com.ahni.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record LectureImportRequest(
    @Schema(description = "같은 학기의 검수된 강의", example = """
        [{"courseEntityId":"00000000-0000-0000-0000-000000000101",
          "professorEntityId":"00000000-0000-0000-0000-000000000801",
          "academicYear":2026,"term":"SECOND","section":"001",
          "schedules":[{"dayOfWeek":1,"startTime":"09:30","endTime":"11:00","classroom":"101호"}]}]
        """)
    @NotNull @Size(min = 1, max = 200) List<@NotNull @Valid LectureRequest> lectures
) { }
