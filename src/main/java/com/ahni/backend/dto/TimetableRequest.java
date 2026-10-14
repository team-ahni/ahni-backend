package com.ahni.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TimetableRequest(@NotNull UUID lectureEntityId) { }
