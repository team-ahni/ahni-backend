package com.ahni.backend.controller;

import com.ahni.backend.config.SecurityConfiguration;
import com.ahni.backend.dto.*;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.service.LectureService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminLectureController.class)
@Import(SecurityConfiguration.class)
@Tag("integration")
class AdminLectureControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean LectureService service;
    @MockitoBean JwtDecoder decoder;

    @Test void lectureRegistrationUsesNameWithoutProfessorIdentity() throws Exception {
        var user = UUID.randomUUID();
        var course = UUID.randomUUID();
        mvc.perform(post("/api/v1/admin/lectures")
            .with(jwt().jwt(t -> t.subject(user.toString())))
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"courseEntityId":"%s","professorName":"남춘성,이우형",
                 "academicYear":2026,"term":"SECOND","section":"001",
                 "schedules":[{"dayOfWeek":1,"startTime":"09:00","endTime":"10:00"}]}
                """.formatted(course)))
            .andExpect(status().isCreated());
        verify(service).create(eq(user), argThat(request -> request.professorName().equals("남춘성,이우형")));
    }

    @Test void nonAdminCannotRegisterLecture() throws Exception {
        var user = UUID.randomUUID();
        when(service.create(eq(user), any())).thenThrow(new AdminAccessDeniedException());
        mvc.perform(post("/api/v1/admin/lectures")
            .with(jwt().jwt(t -> t.subject(user.toString())))
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"courseEntityId":"%s","professorName":"교수",
                 "academicYear":2026,"term":"SECOND","section":"001",
                 "schedules":[{"dayOfWeek":1,"startTime":"09:00","endTime":"10:00"}]}
                """.formatted(UUID.randomUUID())))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test void malformedLectureAndMissingAuthenticationAreRejected() throws Exception {
        mvc.perform(post("/api/v1/admin/lectures/import").with(jwt())
            .contentType(MediaType.APPLICATION_JSON).content("{\"lectures\":[]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/admin/lectures").with(jwt())
            .contentType(MediaType.APPLICATION_JSON).content("{\"academicYear\":1999,\"schedules\":[]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/admin/lectures").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
