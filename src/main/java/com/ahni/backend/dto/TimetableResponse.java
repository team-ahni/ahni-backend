package com.ahni.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record TimetableResponse(UUID entityId, LectureResponse lecture, Instant createdAt, Instant updatedAt) { }
