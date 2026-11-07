package com.ahni.backend.dto;

import com.ahni.backend.domain.StudentRelinkStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record AdminStudentRelinkRequestResponse(
    @Schema(description = "재연결 요청 식별자")
    UUID entityId,

    @Schema(description = "새 계정의 인증 이메일")
    String email,

    @Schema(description = "요청 상태")
    StudentRelinkStatus status,

    @Schema(description = "요청 시각")
    Instant createdAt,

    @Schema(description = "처리 시각", nullable = true)
    Instant resolvedAt,

    @Schema(description = "처리한 관리자", nullable = true)
    AdminIdentityResponse resolvedBy,

    @Schema(description = "연결 대상인 기존 학생 프로필. 본인 확인에 사용합니다.")
    StudentProfileResponse student,

    @Schema(description = "기존 프로필 등록 시각")
    Instant studentCreatedAt
) { }
