package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record InquiryAttachmentResponse(
    @Schema(description = "첨부파일 식별자")
    UUID entityId,

    @Schema(description = "표시용 파일명", example = "grade-screen.png")
    String filename,

    @Schema(description = "서버가 검증한 파일 형식", allowableValues = {"image/jpeg", "image/png", "application/pdf"})
    String contentType,

    @Schema(description = "파일 크기(바이트)", example = "182044")
    long sizeBytes,

    @Schema(description = "등록 시각")
    Instant createdAt
) { }
