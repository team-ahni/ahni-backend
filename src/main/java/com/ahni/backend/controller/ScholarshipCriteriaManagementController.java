package com.ahni.backend.controller;

import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.dto.ScholarshipCriteriaCreateRequest;
import com.ahni.backend.dto.ScholarshipCriteriaResponse;
import com.ahni.backend.dto.ScholarshipCriteriaUpdateRequest;
import com.ahni.backend.service.ScholarshipCriteriaManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/scholarship-criteria")
public class ScholarshipCriteriaManagementController {
    private final ScholarshipCriteriaManagementService service;

    public ScholarshipCriteriaManagementController(ScholarshipCriteriaManagementService service) {
        this.service = service;
    }

    @Operation(
        summary = "관리자용 장학 기준 목록 조회",
        description = "[관리자 인증 O] 학과와 입학연도로 장학 기준을 필터링합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "장학 기준 목록 조회 성공"),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = """
                {"code":"ADMIN_ACCESS_DENIED","message":"관리자 권한이 필요합니다."}
                """)
        ))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ScholarshipCriteriaResponse> findScholarshipCriteria(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) UUID departmentEntityId,
        @RequestParam(required = false) Integer admissionYear
    ) {
        return service.findAll(UUID.fromString(jwt.getSubject()), departmentEntityId, admissionYear);
    }

    @Operation(
        summary = "장학 기준 등록",
        description = "[관리자 인증 O] 학과와 입학연도에 적용할 장학 기준을 등록합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "장학 기준 등록 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = """
                {"code":"INVALID_REQUEST","message":"요청값이 올바르지 않습니다."}
                """)
        )),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = """
                {"code":"ADMIN_ACCESS_DENIED","message":"관리자 권한이 필요합니다."}
                """)
        )),
        @ApiResponse(responseCode = "404", description = "학과 없음", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = """
                {"code":"DEPARTMENT_NOT_FOUND","message":"학과를 찾을 수 없습니다."}
                """)
        )),
        @ApiResponse(responseCode = "409", description = "장학 기준 중복", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = """
                {"code":"SCHOLARSHIP_CRITERIA_ALREADY_EXISTS","message":"같은 학과, 입학연도, 이름의 장학 기준이 이미 있습니다."}
                """)
        ))
    })
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ScholarshipCriteriaResponse createScholarshipCriteria(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ScholarshipCriteriaCreateRequest request
    ) {
        return service.create(UUID.fromString(jwt.getSubject()), request);
    }

    @Operation(
        summary = "장학 기준 수정",
        description = "[관리자 인증 O] 장학금 이름과 기준값을 수정합니다. 학과와 입학연도는 바꿀 수 없습니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "장학 기준 수정 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class)
        )),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class)
        )),
        @ApiResponse(responseCode = "404", description = "장학 기준 없음", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = """
                {"code":"SCHOLARSHIP_CRITERIA_NOT_FOUND","message":"장학 기준을 찾을 수 없습니다."}
                """)
        )),
        @ApiResponse(responseCode = "409", description = "장학 기준 이름 중복", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class)
        ))
    })
    @PutMapping(
        value = "/{criteriaEntityId}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ScholarshipCriteriaResponse updateScholarshipCriteria(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID criteriaEntityId,
        @Valid @RequestBody ScholarshipCriteriaUpdateRequest request
    ) {
        return service.update(UUID.fromString(jwt.getSubject()), criteriaEntityId, request);
    }

    @Operation(
        summary = "장학 기준 삭제",
        description = "[관리자 인증 O] 장학 기준을 삭제합니다. 판정 결과는 저장하지 않으므로 함께 지울 데이터가 없습니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "장학 기준 삭제 성공"),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class)
        )),
        @ApiResponse(responseCode = "404", description = "장학 기준 없음", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class)
        ))
    })
    @DeleteMapping("/{criteriaEntityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScholarshipCriteria(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID criteriaEntityId) {
        service.delete(UUID.fromString(jwt.getSubject()), criteriaEntityId);
    }
}
