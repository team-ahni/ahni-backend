package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record InquiryAssigneeRequest(
    @Schema(
        description = "담당 관리자 식별자. null이면 담당자를 해제합니다.",
        nullable = true,
        example = "00000000-0000-0000-0000-000000000011"
    )
    UUID adminEntityId
) { }
