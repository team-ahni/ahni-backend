package com.ahni.backend.controller;

import com.ahni.backend.dto.StudentProfileRegistrationRequest;
import com.ahni.backend.dto.StudentProfileResponse;
import com.ahni.backend.dto.StudentMajorUpdateRequest;
import com.ahni.backend.dto.StudentRelinkRequestResponse;
import com.ahni.backend.service.StudentRelinkService;
import com.ahni.backend.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students")
public class StudentController {
    private final StudentService studentService;
    private final StudentRelinkService relinkService;

    public StudentController(StudentService studentService, StudentRelinkService relinkService) {
        this.studentService = studentService;
        this.relinkService = relinkService;
    }

    @Operation(
        summary = "기존 학생 프로필 재연결 요청",
        description = "[인증 O] 같은 이메일의 Supabase 계정이 다시 만들어져 프로필 등록이 "
            + "STUDENT_EMAIL_ALREADY_REGISTERED로 막힌 학생이 기존 프로필을 이 계정에 연결해 달라고 요청합니다. "
            + "새 계정 식별자와 이메일은 JWT에서 읽습니다. 관리자가 본인임을 확인하고 승인해야 연결되며, "
            + "같은 계정의 대기 중인 요청이 있으면 새로 만들지 않고 그 요청을 돌려줍니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "202",
            description = "재연결 요청 접수(또는 기존 대기 요청)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = StudentRelinkRequestResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "entityId": "00000000-0000-0000-0000-000000000030",
                      "status": "PENDING",
                      "createdAt": "2026-11-07T01:00:00Z"
                    }
                    """)
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "이 이메일로 등록된 기존 학생 프로필이 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.ahni.backend.dto.ApiErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"code":"STUDENT_NOT_FOUND","message":"학생 프로필을 찾을 수 없습니다."}
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "이 계정에 이미 학생 프로필이 있음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.ahni.backend.dto.ApiErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"code":"STUDENT_ALREADY_REGISTERED","message":"이미 가입된 학생입니다."}
                    """)
            )
        )
    })
    @PostMapping("/me/relink-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StudentRelinkRequestResponse requestRelink(@AuthenticationPrincipal Jwt jwt) {
        return relinkService.request(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"));
    }

    @Operation(
        summary = "내 학생 프로필 조회",
        description = "[인증 O] JWT의 사용자 식별자로 학생 프로필과 전공 구성을 조회합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "학생 프로필 조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = StudentProfileResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "studentEntityId": "00000000-0000-0000-0000-000000000020",
                      "email": "student@inha.edu",
                      "nickname": "인하",
                      "primaryDepartment": {
                        "entityId": "00000000-0000-0000-0000-000000000001",
                        "name": "소프트웨어융합공학과"
                      },
                      "doubleMajorDepartment": {
                        "entityId": "00000000-0000-0000-0000-000000000002",
                        "name": "금융투자학과"
                      },
                      "minorDepartment": {
                        "entityId": "00000000-0000-0000-0000-000000000003",
                        "name": "산업경영학과"
                      },
                      "admissionYear": 2024,
                      "enrollmentStatus": "ENROLLED",
                      "accountStatus": "ACTIVE"
                    }
                    """)
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "등록된 학생 프로필이 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.ahni.backend.dto.ApiErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"code":"STUDENT_NOT_FOUND","message":"학생 프로필을 찾을 수 없습니다."}
                    """)
            )
        )
    })
    @GetMapping("/me")
    public StudentProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return studentService.getProfile(UUID.fromString(jwt.getSubject()));
    }

    @Operation(
        summary = "학생 프로필 등록",
        description = "[인증 O] JWT의 사용자 정보로 학생 프로필과 전공 구성을 등록합니다.",
        security = @SecurityRequirement(name = "bearerAuth"),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = StudentProfileRegistrationRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "doubleMajorDepartmentEntityId": "00000000-0000-0000-0000-000000000002",
                      "minorDepartmentEntityId": "00000000-0000-0000-0000-000000000003",
                      "admissionYear": 2024,
                      "enrollmentStatus": "ENROLLED",
                      "nickname": "인하"
                    }
                    """)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "학생 프로필 등록 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = StudentProfileResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "studentEntityId": "00000000-0000-0000-0000-000000000020",
                      "email": "student@inha.edu",
                      "nickname": "인하",
                      "primaryDepartment": {
                        "entityId": "00000000-0000-0000-0000-000000000001",
                        "name": "소프트웨어융합공학과"
                      },
                      "doubleMajorDepartment": {
                        "entityId": "00000000-0000-0000-0000-000000000002",
                        "name": "금융투자학과"
                      },
                      "minorDepartment": {
                        "entityId": "00000000-0000-0000-0000-000000000003",
                        "name": "산업경영학과"
                      },
                      "admissionYear": 2024,
                      "enrollmentStatus": "ENROLLED",
                      "accountStatus": "ACTIVE"
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청값, 재학 상태 또는 전공 구성 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.ahni.backend.dto.ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "요청값 오류", value = """
                        {"code":"INVALID_REQUEST","message":"요청값이 올바르지 않습니다."}
                        """),
                    @ExampleObject(name = "재학 상태 오류", value = """
                        {"code":"INVALID_ENROLLMENT_STATUS","message":"재학 또는 휴학 상태만 선택할 수 있습니다."}
                        """),
                    @ExampleObject(name = "전공 학과 중복", value = """
                        {"code":"DUPLICATE_MAJOR_DEPARTMENT","message":"같은 학과를 여러 전공 유형으로 선택할 수 없습니다."}
                        """)
                }
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "학과를 찾을 수 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.ahni.backend.dto.ApiErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"code":"DEPARTMENT_NOT_FOUND","message":"학과를 찾을 수 없습니다."}
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "이미 등록된 학생 또는 이메일",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.ahni.backend.dto.ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "학생 중복", value = """
                        {"code":"STUDENT_ALREADY_REGISTERED","message":"이미 가입된 학생입니다."}
                        """),
                    @ExampleObject(name = "이메일 중복", value = """
                        {"code":"STUDENT_EMAIL_ALREADY_REGISTERED","message":"이 이메일로 등록된 학생 정보가 이미 있습니다."}
                        """)
                }
            )
        )
    })
    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentProfileResponse registerProfile(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody StudentProfileRegistrationRequest request
    ) {
        return studentService.registerProfile(
            UUID.fromString(jwt.getSubject()),
            jwt.getClaimAsString("email"),
            request
        );
    }

    @Operation(
        summary = "내 전공 구성 변경",
        description = "[인증 O] JWT의 사용자 식별자로 주전공, 복수전공, 부전공 구성을 전체 교체합니다.",
        security = @SecurityRequirement(name = "bearerAuth"),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = StudentMajorUpdateRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "primaryDepartmentEntityId": "00000000-0000-0000-0000-000000000001",
                      "doubleMajorDepartmentEntityId": null,
                      "minorDepartmentEntityId": "00000000-0000-0000-0000-000000000003"
                    }
                    """)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "전공 구성 변경 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = StudentProfileResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "studentEntityId": "00000000-0000-0000-0000-000000000020",
                      "email": "student@inha.edu",
                      "nickname": "인하",
                      "primaryDepartment": {
                        "entityId": "00000000-0000-0000-0000-000000000001",
                        "name": "소프트웨어융합공학과"
                      },
                      "doubleMajorDepartment": null,
                      "minorDepartment": {
                        "entityId": "00000000-0000-0000-0000-000000000003",
                        "name": "산업경영학과"
                      },
                      "admissionYear": 2024,
                      "enrollmentStatus": "ENROLLED",
                      "accountStatus": "ACTIVE"
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청값 또는 전공 구성 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.ahni.backend.dto.ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "요청값 오류", value = """
                        {"code":"INVALID_REQUEST","message":"요청값이 올바르지 않습니다."}
                        """),
                    @ExampleObject(name = "전공 학과 중복", value = """
                        {"code":"DUPLICATE_MAJOR_DEPARTMENT","message":"같은 학과를 여러 전공 유형으로 선택할 수 없습니다."}
                        """)
                }
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "학생 프로필 또는 학과를 찾을 수 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.ahni.backend.dto.ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "학생 없음", value = """
                        {"code":"STUDENT_NOT_FOUND","message":"학생 프로필을 찾을 수 없습니다."}
                        """),
                    @ExampleObject(name = "학과 없음", value = """
                        {"code":"DEPARTMENT_NOT_FOUND","message":"학과를 찾을 수 없습니다."}
                        """)
                }
            )
        )
    })
    @PutMapping("/me/majors")
    public StudentProfileResponse replaceMajors(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody StudentMajorUpdateRequest request
    ) {
        return studentService.replaceMajors(
            UUID.fromString(jwt.getSubject()),
            request
        );
    }
}
