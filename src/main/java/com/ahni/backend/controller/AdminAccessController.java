package com.ahni.backend.controller;

import com.ahni.backend.dto.AdminIdentityResponse;
import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.service.AdminAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAccessController {
    private final AdminAccessService service;

    public AdminAccessController(AdminAccessService service) {
        this.service = service;
    }

    @Operation(
        summary = "현재 관리자 정보 조회",
        description = "JWT 사용자 ID가 활성 관리자 계정에 연결됐는지 확인하고 본인 관리자 정보를 반환합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "활성 관리자 확인 성공",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = AdminIdentityResponse.class)
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = """
                {"code":"ADMIN_ACCESS_DENIED","message":"관리자 권한이 필요합니다."}
                """)
        ))
    })
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminIdentityResponse getCurrentAdmin(
        @AuthenticationPrincipal Jwt jwt
    ) {
        return service.getCurrentAdmin(UUID.fromString(jwt.getSubject()));
    }

    @Operation(
        summary = "활성 관리자 목록 조회",
        description = "[관리자 인증 O] 문의 담당자 선택에 쓸 활성 관리자를 이름순으로 반환합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "활성 관리자 목록 조회 성공",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                array = @ArraySchema(schema = @Schema(implementation = AdminIdentityResponse.class))
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
        @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(value = """
                {"code":"ADMIN_ACCESS_DENIED","message":"관리자 권한이 필요합니다."}
                """)
        ))
    })
    @GetMapping(value = "/admins", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AdminIdentityResponse> findActiveAdmins(
        @AuthenticationPrincipal Jwt jwt
    ) {
        return service.findActiveAdmins(UUID.fromString(jwt.getSubject()));
    }
}
