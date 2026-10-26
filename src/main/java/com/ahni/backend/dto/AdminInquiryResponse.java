package com.ahni.backend.dto;

import com.ahni.backend.domain.InquiryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record AdminInquiryResponse(
    @Schema(description = "문의 식별자")
    UUID entityId,

    @Schema(description = "문의 학생")
    AdminInquiryStudentResponse student,

    @Schema(description = "문의 제목")
    String title,

    @Schema(description = "문의 내용")
    String content,

    @Schema(description = "처리 상태")
    InquiryStatus status,

    @Schema(description = "담당 관리자. 지정 전이면 null", nullable = true)
    AdminIdentityResponse assignedAdmin,

    @Schema(description = "관리자 답변. 아직 답변 전이면 null", nullable = true)
    String answer,

    @Schema(description = "답변 관리자 이름. 아직 답변 전이면 null", nullable = true)
    String answeredByAdminName,

    @Schema(description = "답변 시각. 아직 답변 전이면 null", nullable = true)
    Instant answeredAt,

    @Schema(description = "등록 시각")
    Instant createdAt,

    @Schema(description = "수정 시각")
    Instant updatedAt,

    @Schema(description = "학생 화면 삭제 시각. 삭제 전이면 null", nullable = true)
    Instant deletedAt
) { }
