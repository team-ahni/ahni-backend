package com.ahni.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ProfessorResponse(UUID entityId, String name, @Schema(nullable = true) String office) { }
