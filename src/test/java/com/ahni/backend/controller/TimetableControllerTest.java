package com.ahni.backend.controller;

import com.ahni.backend.config.SecurityConfiguration;
import com.ahni.backend.dto.TimetableRequest;
import com.ahni.backend.exception.TimetableConflictException;
import com.ahni.backend.service.TimetableService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TimetableController.class)
@Import(SecurityConfiguration.class)
@Tag("integration")
class TimetableControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TimetableService service;
    @MockitoBean JwtDecoder decoder;
    @Test void requiresAuthenticationAndValidInput() throws Exception {
        mvc.perform(get("/api/v1/timetable?academicYear=2026&term=SECOND")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/timetable").with(jwt())
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }
    @Test void conflictUsesStableCode() throws Exception {
        var user = UUID.randomUUID(); var lecture = UUID.randomUUID();
        when(service.create(user, new TimetableRequest(lecture)))
            .thenThrow(new TimetableConflictException("시간이 겹칩니다."));
        mvc.perform(post("/api/v1/timetable").with(jwt().jwt(t -> t.subject(user.toString())))
            .contentType(MediaType.APPLICATION_JSON).content("{\"lectureEntityId\":\"" + lecture + "\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_CONFLICT"));
    }
    @Test void deleteDerivesOwnershipFromJwt() throws Exception {
        var user = UUID.randomUUID(); var entry = UUID.randomUUID();
        mvc.perform(delete("/api/v1/timetable/" + entry).with(jwt().jwt(t -> t.subject(user.toString()))))
            .andExpect(status().isNoContent());
        verify(service).delete(user, entry);
    }
}
