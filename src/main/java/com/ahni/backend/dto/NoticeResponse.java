package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import java.util.UUID;

public record NoticeResponse(
    UUID entityId, String title, String body, Instant updatedAt,
    @Schema(types = {"string", "null"}, format = "date-time") Instant publishedAt, long version,
    @Schema(types = {"string", "null"}, format = "date-time") Instant createdAt,
    @Schema(types = {"string", "null"}) String createdByName,
    @Schema(types = {"object", "null"}, description = "Tiptap document. Image attrs contain imageId and alt.") JsonNode bodyJson
) {}
