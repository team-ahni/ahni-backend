package com.ahni.backend.controller;

import com.ahni.backend.config.SecurityConfiguration;
import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.ScholarshipEligibilityResponse;
import com.ahni.backend.dto.ScholarshipResultResponse;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.service.ScholarshipEligibilityService;
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

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScholarshipEligibilityController.class)
@Import(SecurityConfiguration.class)
@Tag("integration")
class ScholarshipEligibilityControllerTest {
    private static final UUID AUTH_USER_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000010"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScholarshipEligibilityService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void 인증된_학생이_장학금별_충족_여부와_부족분을_조회한다() throws Exception {
        when(service.getEligibility(AUTH_USER_ID)).thenReturn(response());

        mockMvc.perform(get("/api/v1/scholarship-eligibility")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.department.name").value("소프트웨어융합공학과"))
            .andExpect(jsonPath("$.gpa").value(3.20))
            .andExpect(jsonPath("$.warningCount").value(0))
            .andExpect(jsonPath("$.scholarships[0].name").value("성적우수장학금"))
            .andExpect(jsonPath("$.scholarships[0].gpaShortfall").value(0.30))
            .andExpect(jsonPath("$.scholarships[0].met").value(false));
    }

    @Test
    void 인증이_없으면_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/scholarship-eligibility"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 학생_프로필이_없으면_404를_반환한다() throws Exception {
        when(service.getEligibility(AUTH_USER_ID)).thenThrow(new StudentNotFoundException());

        mockMvc.perform(get("/api/v1/scholarship-eligibility")
                .with(jwt().jwt(token -> token.subject(AUTH_USER_ID.toString()))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    private ScholarshipEligibilityResponse response() {
        return new ScholarshipEligibilityResponse(
            2024,
            new DepartmentResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "소프트웨어융합공학과"
            ),
            new BigDecimal("3.20"),
            new BigDecimal("42.0"),
            0,
            List.of(new ScholarshipResultResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000501"),
                "성적우수장학금",
                new BigDecimal("3.50"),
                new BigDecimal("12.0"),
                0,
                new BigDecimal("0.30"),
                new BigDecimal("0.0"),
                0,
                false
            ))
        );
    }
}
