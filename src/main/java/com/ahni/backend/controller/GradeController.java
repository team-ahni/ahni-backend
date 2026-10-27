package com.ahni.backend.controller;

import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.dto.GradeRegistrationRequest;
import com.ahni.backend.dto.GradeResponse;
import com.ahni.backend.dto.GradeSummaryResponse;
import com.ahni.backend.dto.GradeUpdateRequest;
import com.ahni.backend.dto.GradeSimulationRequest;
import com.ahni.backend.dto.GradeSimulationResponse;
import com.ahni.backend.service.GradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/grades")
public class GradeController {
    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @Operation(
        summary = "내 평점 시뮬레이션",
        description = "[인증 O] 본인 실제 성적에 예상 성적을 더해 현재·예상 GPA를 비교합니다. "
            + "실제 성적과 재수강 관계를 변경하거나 예상 성적을 저장하지 않습니다. "
            + "4.5점 만점, 학점 가중 평균, 소수점 두 자리 반올림. 기존 재수강/RPL 규칙을 유지하며 "
            + "예상 P/NP는 GPA에서 제외하고 F는 0점으로 포함합니다. 예상 재수강/RPL은 지원하지 않습니다.",
        security = @SecurityRequirement(name = "bearerAuth"),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = GradeSimulationRequest.class),
                examples = @ExampleObject(value = """
                    {"expectedGrades":[{"category":"MAJOR","credit":3.0,"gradeCode":"A_PLUS"}]}
                    """)))
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "계산 성공, 실제 성적 변경 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GradeSimulationResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "current":{"gpa":3.00,"completedCredits":3.0,"gpaCredits":3.0,
                        "categories":[
                          {"category":"MAJOR","gpa":0.00,"completedCredits":0.0,"gpaCredits":0.0},
                          {"category":"GENERAL_EDUCATION","gpa":0.00,"completedCredits":0.0,"gpaCredits":0.0},
                          {"category":"ELECTIVE","gpa":3.00,"completedCredits":3.0,"gpaCredits":3.0}]},
                      "projected":{"gpa":3.75,"completedCredits":6.0,"gpaCredits":6.0,
                        "categories":[
                          {"category":"MAJOR","gpa":4.50,"completedCredits":3.0,"gpaCredits":3.0},
                          {"category":"GENERAL_EDUCATION","gpa":0.00,"completedCredits":0.0,"gpaCredits":0.0},
                          {"category":"ELECTIVE","gpa":3.00,"completedCredits":3.0,"gpaCredits":3.0}]}
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "예상 성적 1~50건, 분류/등급 필수, 학점 0 초과 30 이하·소수 한 자리",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청값이 올바르지 않습니다.\"}"))),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "404", description = "학생 프로필을 찾을 수 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"STUDENT_NOT_FOUND\",\"message\":\"학생 프로필을 찾을 수 없습니다.\"}")))
    })
    @PostMapping("/simulation")
    public GradeSimulationResponse simulate(@AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody GradeSimulationRequest request) {
        return gradeService.simulate(UUID.fromString(jwt.getSubject()), request);
    }

    @Operation(
        summary = "내 성적 등록",
        description = "[인증 O] JWT의 사용자에게 과목별 학기 성적을 등록합니다. 선택한 연도와 학기에 개설된 강의의 과목만 허용하며 RPL도 같은 검증을 적용합니다.",
        security = @SecurityRequirement(name = "bearerAuth"),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GradeRegistrationRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "courseEntityId": "00000000-0000-0000-0000-000000000101",
                      "academicYear": 2025,
                      "term": "SECOND",
                      "gradeCode": "A_PLUS",
                      "credit": 3.0,
                      "rpl": false,
                      "replacedGradeEntityId": null
                    }
                    """)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "성적 등록 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GradeResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "entityId": "00000000-0000-0000-0000-000000000201",
                      "course": {
                        "entityId": "00000000-0000-0000-0000-000000000101",
                        "code": "CSE101",
                        "name": "프로그래밍 기초",
                        "category": "MAJOR",
                        "department": {
                          "entityId": "00000000-0000-0000-0000-000000000001",
                          "name": "소프트웨어융합공학과"
                        }
                      },
                      "academicYear": 2025,
                      "term": "SECOND",
                      "gradeCode": "A_PLUS",
                      "gradePoint": 4.50,
                      "credit": 3.0,
                      "rpl": false,
                      "replacedGradeEntityId": null,
                      "createdAt": "2026-09-16T00:00:00Z",
                      "updatedAt": "2026-09-16T00:00:00Z"
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청값 또는 성적 규칙 오류. INVALID_GRADE: 선택한 학기에 개설되지 않은 과목",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "요청값 오류", value = """
                        {"code":"INVALID_REQUEST","message":"요청값이 올바르지 않습니다."}
                        """),
                    @ExampleObject(name = "성적 규칙 오류", value = """
                        {"code":"INVALID_GRADE","message":"일반 성적에는 등급이 필수입니다."}
                        """),
                    @ExampleObject(name = "다른 연도 과목", value = "{\"code\":\"INVALID_GRADE\",\"message\":\"선택한 연도와 학기에 개설되지 않은 과목입니다.\"}")
                }
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "학생 프로필, 과목 또는 재수강 대상 성적을 찾을 수 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "학생 없음", value = """
                        {"code":"STUDENT_NOT_FOUND","message":"학생 프로필을 찾을 수 없습니다."}
                        """),
                    @ExampleObject(name = "과목 없음", value = """
                        {"code":"COURSE_NOT_FOUND","message":"과목을 찾을 수 없습니다."}
                        """),
                    @ExampleObject(name = "재수강 대상 없음", value = """
                        {"code":"GRADE_NOT_FOUND","message":"성적을 찾을 수 없습니다."}
                        """)
                }
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "같은 학기 성적 중복 또는 재수강 대상 충돌",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "성적 중복", value = """
                        {"code":"GRADE_ALREADY_REGISTERED","message":"해당 학기의 과목 성적이 이미 등록되어 있습니다."}
                        """),
                    @ExampleObject(name = "재수강 대상 충돌", value = """
                        {"code":"GRADE_REPLACEMENT_CONFLICT","message":"이미 다른 재수강 성적에 연결된 성적입니다."}
                        """)
                }
            )
        )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GradeResponse register(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody GradeRegistrationRequest request
    ) {
        return gradeService.register(UUID.fromString(jwt.getSubject()), request);
    }

    @Operation(
        summary = "내 성적 목록 조회",
        description = "[인증 O] JWT의 사용자에게 등록된 성적을 최신 학기순으로 조회합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "성적 목록 조회 성공",
            content = @Content(
                mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = GradeResponse.class)),
                examples = @ExampleObject(value = """
                    [
                      {
                        "entityId": "00000000-0000-0000-0000-000000000201",
                        "course": {
                          "entityId": "00000000-0000-0000-0000-000000000101",
                          "code": "CSE101",
                          "name": "프로그래밍 기초",
                          "category": "MAJOR",
                          "department": {
                            "entityId": "00000000-0000-0000-0000-000000000001",
                            "name": "소프트웨어융합공학과"
                          }
                        },
                        "academicYear": 2025,
                        "term": "SECOND",
                        "gradeCode": "A_PLUS",
                        "gradePoint": 4.50,
                        "credit": 3.0,
                        "rpl": false,
                        "replacedGradeEntityId": null,
                        "createdAt": "2026-09-16T00:00:00Z",
                        "updatedAt": "2026-09-16T00:00:00Z"
                      }
                    ]
                    """)
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "학생 프로필을 찾을 수 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"code":"STUDENT_NOT_FOUND","message":"학생 프로필을 찾을 수 없습니다."}
                    """)
            )
        )
    })
    @GetMapping
    public List<GradeResponse> getGrades(@AuthenticationPrincipal Jwt jwt) {
        return gradeService.getGrades(UUID.fromString(jwt.getSubject()));
    }

    @Operation(
        summary = "내 GPA 요약 조회",
        description = "[인증 O] 재수강으로 대체된 이전 성적을 제외하고 전체 및 과목 분류별 GPA와 이수학점을 계산합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "GPA 요약 조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GradeSummaryResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "gpa": 3.83,
                      "completedCredits": 42.0,
                      "gpaCredits": 36.0,
                      "categories": [
                        {
                          "category": "MAJOR",
                          "gpa": 4.02,
                          "completedCredits": 24.0,
                          "gpaCredits": 21.0
                        },
                        {
                          "category": "GENERAL_EDUCATION",
                          "gpa": 3.50,
                          "completedCredits": 12.0,
                          "gpaCredits": 9.0
                        },
                        {
                          "category": "ELECTIVE",
                          "gpa": 3.00,
                          "completedCredits": 6.0,
                          "gpaCredits": 6.0
                        }
                      ]
                    }
                    """)
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "학생 프로필을 찾을 수 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"code":"STUDENT_NOT_FOUND","message":"학생 프로필을 찾을 수 없습니다."}
                    """)
            )
        )
    })
    @GetMapping("/summary")
    public GradeSummaryResponse getSummary(@AuthenticationPrincipal Jwt jwt) {
        return gradeService.getSummary(UUID.fromString(jwt.getSubject()));
    }

    @Operation(
        summary = "내 성적 수정",
        description = "[인증 O] 본인이 등록한 성적의 학기, 등급, 학점, RPL 및 재수강 대상 성적을 수정합니다. 과목은 변경하지 않습니다. 연도 또는 학기가 바뀌면 해당 학기 개설 여부를 재검증합니다. 기존 연도와 학기를 유지하면 개설 자료가 없어도 수정할 수 있습니다.",
        security = @SecurityRequirement(name = "bearerAuth"),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GradeUpdateRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "academicYear": 2024,
                      "term": "WINTER",
                      "gradeCode": "B_PLUS",
                      "credit": 2.0,
                      "rpl": false,
                      "replacedGradeEntityId": "00000000-0000-0000-0000-000000000200"
                    }
                    """)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "성적 수정 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GradeResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "entityId": "00000000-0000-0000-0000-000000000201",
                      "course": {
                        "entityId": "00000000-0000-0000-0000-000000000101",
                        "code": "CSE101",
                        "name": "프로그래밍 기초",
                        "category": "MAJOR",
                        "department": {
                          "entityId": "00000000-0000-0000-0000-000000000001",
                          "name": "소프트웨어융합공학과"
                        }
                      },
                      "academicYear": 2024,
                      "term": "WINTER",
                      "gradeCode": "B_PLUS",
                      "gradePoint": 3.50,
                      "credit": 2.0,
                      "rpl": false,
                      "replacedGradeEntityId": "00000000-0000-0000-0000-000000000200",
                      "createdAt": "2026-09-16T00:00:00Z",
                      "updatedAt": "2026-09-16T01:00:00Z"
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청값 또는 성적 규칙 오류. INVALID_GRADE: 변경한 학기에 개설되지 않은 과목",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "요청값 오류", value = """
                        {"code":"INVALID_REQUEST","message":"요청값이 올바르지 않습니다."}
                        """),
                    @ExampleObject(name = "성적 규칙 오류", value = """
                        {"code":"INVALID_GRADE","message":"RPL 성적에는 등급을 입력할 수 없습니다."}
                        """),
                    @ExampleObject(name = "다른 연도 과목", value = "{\"code\":\"INVALID_GRADE\",\"message\":\"선택한 연도와 학기에 개설되지 않은 과목입니다.\"}")
                }
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "학생 프로필 또는 소유한 성적을 찾을 수 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "학생 없음", value = """
                        {"code":"STUDENT_NOT_FOUND","message":"학생 프로필을 찾을 수 없습니다."}
                        """),
                    @ExampleObject(name = "성적 없음", value = """
                        {"code":"GRADE_NOT_FOUND","message":"성적을 찾을 수 없습니다."}
                        """)
                }
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "수정 결과 같은 학기 성적 중복 또는 재수강 대상 충돌",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "성적 중복", value = """
                        {"code":"GRADE_ALREADY_REGISTERED","message":"해당 학기의 과목 성적이 이미 등록되어 있습니다."}
                        """),
                    @ExampleObject(name = "재수강 대상 충돌", value = """
                        {"code":"GRADE_REPLACEMENT_CONFLICT","message":"이미 다른 재수강 성적에 연결된 성적입니다."}
                        """)
                }
            )
        )
    })
    @PutMapping("/{gradeEntityId}")
    public GradeResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID gradeEntityId,
        @Valid @RequestBody GradeUpdateRequest request
    ) {
        return gradeService.update(
            UUID.fromString(jwt.getSubject()),
            gradeEntityId,
            request
        );
    }

    @Operation(
        summary = "내 성적 삭제",
        description = "[인증 O] 본인이 등록한 성적을 삭제합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "성적 삭제 성공", content = @Content),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "학생 프로필 또는 소유한 성적을 찾을 수 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = {
                    @ExampleObject(name = "학생 없음", value = """
                        {"code":"STUDENT_NOT_FOUND","message":"학생 프로필을 찾을 수 없습니다."}
                        """),
                    @ExampleObject(name = "성적 없음", value = """
                        {"code":"GRADE_NOT_FOUND","message":"성적을 찾을 수 없습니다."}
                        """)
                }
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "재수강으로 대체된 이전 성적은 삭제할 수 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"code":"GRADE_REPLACEMENT_CONFLICT","message":"재수강으로 대체된 이전 성적은 삭제할 수 없습니다."}
                    """)
            )
        )
    })
    @DeleteMapping("/{gradeEntityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID gradeEntityId
    ) {
        gradeService.delete(UUID.fromString(jwt.getSubject()), gradeEntityId);
    }
}
