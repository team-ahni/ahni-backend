package com.ahni.backend.controller;

import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.dto.ScholarshipEligibilityResponse;
import com.ahni.backend.service.ScholarshipEligibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scholarship-eligibility")
public class ScholarshipEligibilityController {
    private final ScholarshipEligibilityService scholarshipEligibilityService;

    public ScholarshipEligibilityController(
        ScholarshipEligibilityService scholarshipEligibilityService
    ) {
        this.scholarshipEligibilityService = scholarshipEligibilityService;
    }

    @Operation(
        summary = "내 장학요건 충족 여부 조회",
        description = "[인증 O] 주전공 학과와 입학연도의 장학 기준을 누적 GPA, 누적 이수학점, "
            + "정규학기 학사경고 횟수와 비교합니다. scholarships가 비어 있으면 등록된 기준이 없습니다. "
            + "결과는 안내용이며 공식 장학 선발 결과가 아닙니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "장학요건 판정 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ScholarshipEligibilityResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "admissionYear": 2024,
                      "department": {
                        "entityId": "00000000-0000-0000-0000-000000000001",
                        "name": "소프트웨어융합공학과"
                      },
                      "gpa": 3.20,
                      "completedCredits": 42.0,
                      "warningCount": 0,
                      "scholarships": [
                        {
                          "criteriaEntityId": "00000000-0000-0000-0000-000000000501",
                          "name": "성적우수장학금",
                          "minGpa": 3.50,
                          "minCredits": 12.0,
                          "maxWarnings": 0,
                          "gpaShortfall": 0.30,
                          "creditShortfall": 0.0,
                          "excessWarnings": 0,
                          "met": false
                        }
                      ]
                    }
                    """)
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "학생 프로필 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"code":"STUDENT_NOT_FOUND","message":"학생 프로필을 찾을 수 없습니다."}
                    """)
            )
        )
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ScholarshipEligibilityResponse getEligibility(@AuthenticationPrincipal Jwt jwt) {
        return scholarshipEligibilityService.getEligibility(UUID.fromString(jwt.getSubject()));
    }
}
