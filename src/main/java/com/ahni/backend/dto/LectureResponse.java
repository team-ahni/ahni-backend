package com.ahni.backend.dto;

import com.ahni.backend.domain.AcademicTerm;
import java.math.BigDecimal;
import java.util.*;

public record LectureResponse(UUID entityId, UUID courseEntityId, String courseCode, String courseName,
    BigDecimal credit, String professorName, int academicYear, AcademicTerm term,
    String section, List<LectureScheduleResponse> schedules) { }
