package com.ahni.backend.dto;

import com.ahni.backend.domain.StudentRelinkStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record StudentRelinkRequestResponse(
    @Schema(description = "재연결 요청 식별자")
    UUID entityId,

    @Schema(description = "요청 상태. 학생이 다시 요청하면 대기 중인 요청을 그대로 돌려줍니다.")
    StudentRelinkStatus status,

    @Schema(description = "요청 시각")
    Instant createdAt
) { }
