package com.ahni.backend.controller;

import com.ahni.backend.config.SecurityConfiguration;
import com.ahni.backend.domain.AccountStatus;
import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.StudentMajorUpdateRequest;
import com.ahni.backend.dto.StudentProfileRegistrationRequest;
import com.ahni.backend.dto.StudentProfileResponse;
import com.ahni.backend.exception.DepartmentNotFoundException;
import com.ahni.backend.exception.DuplicateMajorDepartmentException;
import com.ahni.backend.exception.InvalidEnrollmentStatusException;
import com.ahni.backend.exception.StudentAlreadyRegisteredException;
import com.ahni.backend.exception.StudentEmailAlreadyRegisteredException;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.service.StudentService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentController.class)
@Import(SecurityConfiguration.class)
@Tag("integration")
class StudentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private com.ahni.backend.service.StudentRelinkService relinkService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void 인증된_학생이_자신의_프로필을_조회한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID departmentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID doubleMajorDepartmentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID minorDepartmentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID studentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000020");

        when(studentService.getProfile(authUserId)).thenReturn(new StudentProfileResponse(
            studentEntityId,
            "student@inha.edu",
            "인하",
            new DepartmentResponse(departmentEntityId, "소프트웨어융합공학과"),
            new DepartmentResponse(doubleMajorDepartmentEntityId, "금융투자학과"),
            new DepartmentResponse(minorDepartmentEntityId, "산업경영학과"),
            2024,
            EnrollmentStatus.ENROLLED,
            AccountStatus.ACTIVE
        ));

        mockMvc.perform(get("/api/v1/students/me")
                .with(jwt().jwt(token -> token.subject(authUserId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.studentEntityId").value(studentEntityId.toString()))
            .andExpect(jsonPath("$.email").value("student@inha.edu"))
            .andExpect(jsonPath("$.primaryDepartment.entityId").value(departmentEntityId.toString()))
            .andExpect(jsonPath("$.doubleMajorDepartment.entityId")
                .value(doubleMajorDepartmentEntityId.toString()))
            .andExpect(jsonPath("$.minorDepartment.entityId")
                .value(minorDepartmentEntityId.toString()))
            .andExpect(jsonPath("$.enrollmentStatus").value("ENROLLED"))
            .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        verify(studentService).getProfile(authUserId);
    }

    @Test
    void 인증되지_않은_사용자는_프로필을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/students/me"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(studentService);
    }

    @Test
    void 등록되지_않은_학생의_프로필을_조회하면_404를_반환한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");

        when(studentService.getProfile(authUserId)).thenThrow(new StudentNotFoundException());

        mockMvc.perform(get("/api/v1/students/me")
                .with(jwt().jwt(token -> token.subject(authUserId.toString()))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    void 인증된_학생이_프로필을_등록한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID departmentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID studentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        String email = "student@inha.edu";
        StudentProfileRegistrationRequest request = new StudentProfileRegistrationRequest(
            departmentEntityId,
            null,
            null,
            2024,
            EnrollmentStatus.ENROLLED,
            "인하"
        );

        when(studentService.registerProfile(authUserId, email, request))
            .thenReturn(new StudentProfileResponse(
                studentEntityId,
                email,
                "인하",
                new DepartmentResponse(departmentEntityId, "소프트웨어융합공학과"),
                null,
                null,
                2024,
                EnrollmentStatus.ENROLLED,
                AccountStatus.ACTIVE
            ));

        mockMvc.perform(post("/api/v1/students/me")
                .with(jwt().jwt(token -> token
                    .subject(authUserId.toString())
                    .claim("email", email)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "admissionYear": 2024,
                      "enrollmentStatus": "ENROLLED",
                      "nickname": "인하"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.studentEntityId").value(studentEntityId.toString()))
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.primaryDepartment.entityId").value(departmentEntityId.toString()))
            .andExpect(jsonPath("$.doubleMajorDepartment").doesNotExist())
            .andExpect(jsonPath("$.minorDepartment").doesNotExist())
            .andExpect(jsonPath("$.enrollmentStatus").value("ENROLLED"))
            .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        verify(studentService).registerProfile(authUserId, email, request);
    }

    @Test
    void 인증된_학생이_복수전공과_부전공을_포함해_프로필을_등록한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID primaryDepartmentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID doubleMajorDepartmentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID minorDepartmentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID studentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        String email = "student@inha.edu";
        StudentProfileRegistrationRequest request = new StudentProfileRegistrationRequest(
            primaryDepartmentEntityId,
            doubleMajorDepartmentEntityId,
            minorDepartmentEntityId,
            2024,
            EnrollmentStatus.ENROLLED,
            "인하"
        );
        when(studentService.registerProfile(authUserId, email, request))
            .thenReturn(new StudentProfileResponse(
                studentEntityId,
                email,
                "인하",
                new DepartmentResponse(primaryDepartmentEntityId, "소프트웨어융합공학과"),
                new DepartmentResponse(doubleMajorDepartmentEntityId, "금융투자학과"),
                new DepartmentResponse(minorDepartmentEntityId, "산업경영학과"),
                2024,
                EnrollmentStatus.ENROLLED,
                AccountStatus.ACTIVE
            ));

        mockMvc.perform(post("/api/v1/students/me")
                .with(jwt().jwt(token -> token
                    .subject(authUserId.toString())
                    .claim("email", email)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "doubleMajorDepartmentEntityId": "00000000-0000-0000-0000-000000000002",
                      "minorDepartmentEntityId": "00000000-0000-0000-0000-000000000003",
                      "admissionYear": 2024,
                      "enrollmentStatus": "ENROLLED",
                      "nickname": "인하"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.primaryDepartment.entityId")
                .value(primaryDepartmentEntityId.toString()))
            .andExpect(jsonPath("$.doubleMajorDepartment.entityId")
                .value(doubleMajorDepartmentEntityId.toString()))
            .andExpect(jsonPath("$.minorDepartment.entityId")
                .value(minorDepartmentEntityId.toString()));

        verify(studentService).registerProfile(authUserId, email, request);
    }

    @Test
    void 같은_학과를_여러_전공으로_등록하면_400을_반환한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID departmentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String email = "student@inha.edu";
        StudentProfileRegistrationRequest request = new StudentProfileRegistrationRequest(
            departmentId,
            null,
            departmentId,
            2024,
            EnrollmentStatus.ENROLLED,
            "인하"
        );
        when(studentService.registerProfile(authUserId, email, request))
            .thenThrow(new DuplicateMajorDepartmentException());

        mockMvc.perform(post("/api/v1/students/me")
                .with(jwt().jwt(token -> token
                    .subject(authUserId.toString())
                    .claim("email", email)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "minorDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "admissionYear": 2024,
                      "enrollmentStatus": "ENROLLED",
                      "nickname": "인하"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DUPLICATE_MAJOR_DEPARTMENT"));
    }

    @Test
    void 인증되지_않은_사용자는_프로필을_등록할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/students/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(studentService);
    }

    @Test
    void 필수_요청값이_없으면_프로필을_등록할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/students/me")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(studentService);
    }

    @Test
    void 이미_등록된_학생이면_409를_반환한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID departmentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String email = "student@inha.edu";
        StudentProfileRegistrationRequest request = new StudentProfileRegistrationRequest(
            departmentEntityId,
            null,
            null,
            2024,
            EnrollmentStatus.ENROLLED,
            "인하"
        );

        when(studentService.registerProfile(authUserId, email, request))
            .thenThrow(new StudentAlreadyRegisteredException());

        mockMvc.perform(post("/api/v1/students/me")
                .with(jwt().jwt(token -> token
                    .subject(authUserId.toString())
                    .claim("email", email)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "admissionYear": 2024,
                      "enrollmentStatus": "ENROLLED",
                      "nickname": "인하"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STUDENT_ALREADY_REGISTERED"));
    }

    @Test
    void 이미_등록된_이메일이면_409와_안정적인_오류_코드를_반환한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        String email = "student@inha.edu";

        when(studentService.registerProfile(authUserId, email, new StudentProfileRegistrationRequest(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            null,
            null,
            2024,
            EnrollmentStatus.ENROLLED,
            "인하"
        ))).thenThrow(new StudentEmailAlreadyRegisteredException());

        mockMvc.perform(post("/api/v1/students/me")
                .with(jwt().jwt(token -> token
                    .subject(authUserId.toString())
                    .claim("email", email)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "admissionYear": 2024,
                      "enrollmentStatus": "ENROLLED",
                      "nickname": "인하"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STUDENT_EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void 존재하지_않는_학과이면_404를_반환한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID departmentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String email = "student@inha.edu";
        StudentProfileRegistrationRequest request = new StudentProfileRegistrationRequest(
            departmentEntityId,
            null,
            null,
            2024,
            EnrollmentStatus.ENROLLED,
            "인하"
        );

        when(studentService.registerProfile(authUserId, email, request))
            .thenThrow(new DepartmentNotFoundException());

        mockMvc.perform(post("/api/v1/students/me")
                .with(jwt().jwt(token -> token
                    .subject(authUserId.toString())
                    .claim("email", email)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "admissionYear": 2024,
                      "enrollmentStatus": "ENROLLED",
                      "nickname": "인하"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void 가입할_수_없는_재학_상태이면_400을_반환한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID departmentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String email = "student@inha.edu";
        StudentProfileRegistrationRequest request = new StudentProfileRegistrationRequest(
            departmentEntityId,
            null,
            null,
            2024,
            EnrollmentStatus.GRADUATED,
            "인하"
        );

        when(studentService.registerProfile(authUserId, email, request))
            .thenThrow(new InvalidEnrollmentStatusException());

        mockMvc.perform(post("/api/v1/students/me")
                .with(jwt().jwt(token -> token
                    .subject(authUserId.toString())
                    .claim("email", email)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "admissionYear": 2024,
                      "enrollmentStatus": "GRADUATED",
                      "nickname": "인하"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ENROLLMENT_STATUS"));
    }

    @Test
    void 정의되지_않은_재학_상태이면_400을_반환한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");

        mockMvc.perform(post("/api/v1/students/me")
                .with(jwt().jwt(token -> token
                    .subject(authUserId.toString())
                    .claim("email", "student@inha.edu")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "admissionYear": 2024,
                      "enrollmentStatus": "UNKNOWN",
                      "nickname": "인하"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(studentService);
    }

    @Test
    void 유효하지_않은_입학연도이면_400을_반환한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID departmentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String email = "student@inha.edu";
        StudentProfileRegistrationRequest request = new StudentProfileRegistrationRequest(
            departmentEntityId,
            null,
            null,
            9999,
            EnrollmentStatus.ENROLLED,
            "인하"
        );

        when(studentService.registerProfile(authUserId, email, request))
            .thenThrow(new IllegalArgumentException("입학연도가 올바르지 않습니다."));

        mockMvc.perform(post("/api/v1/students/me")
                .with(jwt().jwt(token -> token
                    .subject(authUserId.toString())
                    .claim("email", email)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "admissionYear": 9999,
                      "enrollmentStatus": "ENROLLED",
                      "nickname": "인하"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 인증된_학생이_전공_구성을_전체_교체한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID primaryDepartmentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID minorDepartmentId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        StudentMajorUpdateRequest request = new StudentMajorUpdateRequest(
            primaryDepartmentId,
            null,
            minorDepartmentId
        );
        when(studentService.replaceMajors(authUserId, request))
            .thenReturn(new StudentProfileResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000020"),
                "student@inha.edu",
                "인하",
                new DepartmentResponse(primaryDepartmentId, "소프트웨어융합공학과"),
                null,
                new DepartmentResponse(minorDepartmentId, "산업경영학과"),
                2024,
                EnrollmentStatus.ENROLLED,
                AccountStatus.ACTIVE
            ));

        mockMvc.perform(put("/api/v1/students/me/majors")
                .with(jwt().jwt(token -> token.subject(authUserId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "doubleMajorDepartmentEntityId": null,
                      "minorDepartmentEntityId": "00000000-0000-0000-0000-000000000003"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.primaryDepartment.entityId")
                .value(primaryDepartmentId.toString()))
            .andExpect(jsonPath("$.doubleMajorDepartment").doesNotExist())
            .andExpect(jsonPath("$.minorDepartment.entityId")
                .value(minorDepartmentId.toString()));

        verify(studentService).replaceMajors(authUserId, request);
    }

    @Test
    void 인증되지_않은_사용자는_전공을_변경할_수_없다() throws Exception {
        mockMvc.perform(put("/api/v1/students/me/majors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(studentService);
    }

    @Test
    void 주전공이_없으면_전공을_변경할_수_없다() throws Exception {
        mockMvc.perform(put("/api/v1/students/me/majors")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(studentService);
    }

    @Test
    void 등록되지_않은_학생의_전공_변경은_404를_반환한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        StudentMajorUpdateRequest request = new StudentMajorUpdateRequest(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            null,
            null
        );
        when(studentService.replaceMajors(authUserId, request))
            .thenThrow(new StudentNotFoundException());

        mockMvc.perform(put("/api/v1/students/me/majors")
                .with(jwt().jwt(token -> token.subject(authUserId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"primaryDepartmentEntityId":"00000000-0000-0000-0000-000000000001"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    void 존재하지_않는_학과로_전공을_변경하면_404를_반환한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        StudentMajorUpdateRequest request = new StudentMajorUpdateRequest(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            null,
            null
        );
        when(studentService.replaceMajors(authUserId, request))
            .thenThrow(new DepartmentNotFoundException());

        mockMvc.perform(put("/api/v1/students/me/majors")
                .with(jwt().jwt(token -> token.subject(authUserId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"primaryDepartmentEntityId":"00000000-0000-0000-0000-000000000001"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void 같은_학과를_여러_전공으로_변경하면_400을_반환한다() throws Exception {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID departmentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        StudentMajorUpdateRequest request = new StudentMajorUpdateRequest(
            departmentId,
            null,
            departmentId
        );
        when(studentService.replaceMajors(authUserId, request))
            .thenThrow(new DuplicateMajorDepartmentException());

        mockMvc.perform(put("/api/v1/students/me/majors")
                .with(jwt().jwt(token -> token.subject(authUserId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "minorDepartmentEntityId": "00000000-0000-0000-0000-000000000001"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DUPLICATE_MAJOR_DEPARTMENT"));
    }
}
