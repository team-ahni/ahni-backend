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

    @Test void professorRegistrationUsesJwtAndValidatedRequest() throws Exception {
        var user = UUID.randomUUID();
        var professor = UUID.randomUUID();
        var request = new ProfessorRequest(null, "교수", null);
        when(service.createProfessor(user, request)).thenReturn(new ProfessorResponse(professor, "교수", null));
        mvc.perform(post("/api/v1/admin/professors")
            .with(jwt().jwt(t -> t.subject(user.toString())))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"교수\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.entityId").value(professor.toString()));
        verify(service).createProfessor(user, request);
    }

    @Test void nonAdminAndUnauthenticatedUsersCannotReadProfessors() throws Exception {
        var user = UUID.randomUUID();
        when(service.professors(user)).thenThrow(new AdminAccessDeniedException());
        mvc.perform(get("/api/v1/admin/professors").with(jwt().jwt(t -> t.subject(user.toString()))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
        mvc.perform(get("/api/v1/admin/professors")).andExpect(status().isUnauthorized());
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
