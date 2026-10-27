package com.ahni.backend.controller;

import com.ahni.backend.config.SecurityConfiguration;
import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.domain.CourseRecommendationReason;
import com.ahni.backend.domain.CurriculumDivision;
import com.ahni.backend.domain.RecommendedTerm;
import com.ahni.backend.dto.CourseRecommendationResponse;
import com.ahni.backend.dto.CourseResponse;
import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.MajorCourseRecommendationResponse;
import com.ahni.backend.dto.RecommendedCourseResponse;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.service.CourseRecommendationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseRecommendationController.class)
@Import(SecurityConfiguration.class)
@Tag("integration")
class CourseRecommendationControllerTest {
    private static final UUID AUTH_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000030");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseRecommendationService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void 학생이_학기별_수강_추천을_조회한다() throws Exception {
        when(service.recommend(AUTH_USER_ID, 2026, AcademicTerm.SECOND)).thenReturn(response());

        mockMvc.perform(get("/api/v1/course-recommendations")
                .param("academicYear", "2026")
                .param("term", "SECOND")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.semesterNumber").value(4))
            .andExpect(jsonPath("$.majors[0].majorType").value("PRIMARY"))
            .andExpect(jsonPath("$.majors[0].courses[0].course.code").value("ITC2209"))
            .andExpect(jsonPath("$.majors[0].courses[0].reasons[0]").value("GRADUATION_REQUIRED"))
            .andExpect(jsonPath("$.majors[0].courses[0].previousGradeCode").doesNotExist());
    }

    @Test
    void 잘못된_학기는_400을_응답한다() throws Exception {
        when(service.recommend(AUTH_USER_ID, 2026, AcademicTerm.SUMMER))
            .thenThrow(new IllegalArgumentException("정규학기 연도와 학기를 입력해 주세요."));

        mockMvc.perform(get("/api/v1/course-recommendations")
                .param("academicYear", "2026")
                .param("term", "SUMMER")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 학생_프로필이_없으면_404를_응답한다() throws Exception {
        when(service.recommend(AUTH_USER_ID, 2026, AcademicTerm.FIRST)).thenThrow(new StudentNotFoundException());

        mockMvc.perform(get("/api/v1/course-recommendations")
                .param("academicYear", "2026")
                .param("term", "FIRST")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    void 인증되지_않은_사용자는_추천을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/course-recommendations")
                .param("academicYear", "2026")
                .param("term", "FIRST"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    private static CourseRecommendationResponse response() {
        DepartmentResponse department = new DepartmentResponse(
            UUID.fromString("00000000-0000-0000-0000-000000000040"), "소프트웨어융합공학과");
        return new CourseRecommendationResponse(2026, AcademicTerm.SECOND, 4, false, List.of(
            new MajorCourseRecommendationResponse("PRIMARY", department, true, List.of(
                new RecommendedCourseResponse(
                    new CourseResponse(UUID.fromString("00000000-0000-0000-0000-000000000041"), "ITC2209",
                        "자료구조와 알고리즘", new BigDecimal("3.0"), CourseCategory.MAJOR, department),
                    CurriculumDivision.MAJOR_REQUIRED,
                    1,
                    RecommendedTerm.SECOND,
                    List.of(CourseRecommendationReason.GRADUATION_REQUIRED, CourseRecommendationReason.OVERDUE),
                    null,
                    List.of()
                )
            ))
        ));
    }
}
