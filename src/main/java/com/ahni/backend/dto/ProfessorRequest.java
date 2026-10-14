package com.ahni.backend.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ProfessorRequest(
    @Schema(nullable = true) UUID departmentEntityId,
    @NotBlank @Size(max = 100) @Schema(example = "김교수") String name,
    @Size(max = 200) @Schema(nullable = true, example = "하이테크관 401호") String office
) { }
