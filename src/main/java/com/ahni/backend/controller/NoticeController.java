package com.ahni.backend.controller;

import com.ahni.backend.dto.ApiErrorResponse;
import com.ahni.backend.dto.NoticeResponse;
import com.ahni.backend.service.NoticeService;
import com.ahni.backend.service.NoticeImageService;
import com.ahni.backend.dto.InquiryAttachmentFile;
import com.ahni.backend.exception.NoticeException;
import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class NoticeController {
    private final NoticeService notices;

    public NoticeController(NoticeService notices) {
        this.notices = notices;
    }

    @Operation(summary = "관리자 공지 목록 조회")
    @ApiResponse(responseCode = "200", description = "공지 목록 조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음 (ADMIN_ACCESS_DENIED)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping(value = "/api/v1/admin/notices", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<NoticeResponse> listAdminNotices(@AuthenticationPrincipal Jwt jwt) {
        return notices.listAdmin(UUID.fromString(jwt.getSubject()));
    }

    @Operation(summary = "학생 공개 공지 목록 조회")
    @ApiResponse(responseCode = "200", description = "공개 공지 목록 조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    @ApiResponse(responseCode = "404", description = "학생 프로필 없음 (STUDENT_NOT_FOUND)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping(value = "/api/v1/notices", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<NoticeResponse> listPublishedNotices(@AuthenticationPrincipal Jwt jwt) {
        return notices.listPublished(UUID.fromString(jwt.getSubject()));
    }

    @Operation(summary = "공지 초안 등록")
    @ApiResponse(responseCode = "201", description = "공지 초안 등록 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음 (ADMIN_ACCESS_DENIED)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (INVALID_REQUEST)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(value = "/api/v1/admin/notices", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public NoticeResponse createNotice(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NoticeCreateRequest request) {
        return notices.create(UUID.fromString(jwt.getSubject()), request.title(), request.body(), request.bodyJson(), List.of());
    }

    @Operation(summary = "공지 수정 및 공개 설정")
    @ApiResponse(responseCode = "200", description = "공지 수정 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음 (ADMIN_ACCESS_DENIED)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (INVALID_REQUEST)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "공지 없음 (NOTICE_NOT_FOUND)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class),
        examples = @ExampleObject(value = """
            {"code":"NOTICE_NOT_FOUND","message":"공지를 찾을 수 없습니다."}
            """)))
    @ApiResponse(responseCode = "409", description = "동시 수정 (NOTICE_VERSION_CONFLICT)", content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class),
        examples = @ExampleObject(value = """
            {"code":"NOTICE_VERSION_CONFLICT","message":"공지가 변경되었습니다. 목록을 새로고침해 주세요."}
            """)))
    @PutMapping(value = "/api/v1/admin/notices/{noticeId}", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public NoticeResponse updateNotice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID noticeId,
        @Valid @RequestBody NoticeUpdateRequest request) {
        return notices.update(UUID.fromString(jwt.getSubject()), noticeId, request.title(), request.body(),
            request.published(), request.version(), request.bodyJson(), List.of());
    }

    @Operation(summary = "공지 삭제", description = "삭제 시간을 기록하고 공지를 조회에서 숨깁니다.")
    @ApiResponse(responseCode = "204", description = "공지 삭제 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    @ApiResponse(responseCode = "403", description = "ADMIN_ACCESS_DENIED")
    @ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "NOTICE_VERSION_CONFLICT")
    @DeleteMapping("/api/v1/admin/notices/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNotice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID noticeId,
        @RequestParam long version) {
        notices.delete(UUID.fromString(jwt.getSubject()), noticeId, version);
    }

    @Operation(summary = "본문 사진과 공지 초안 저장", description = "notice JSON과 images 파일, 같은 순서의 imageIds를 보냅니다. JPG/PNG 최대 10장, 업로드 합계 5MiB.")
    @ApiResponse(responseCode = "201", description = "공지 저장 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_NOTICE_CONTENT 또는 INVALID_REQUEST")
    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
    @ApiResponse(responseCode = "403", description = "ADMIN_ACCESS_DENIED")
    @ApiResponse(responseCode = "413", description = "요청 크기 초과")
    @ApiResponse(responseCode = "503", description = "NOTICE_IMAGE_STORAGE_UNAVAILABLE")
    @PostMapping(value = "/api/v1/admin/notices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeResponse createRichNotice(@AuthenticationPrincipal Jwt jwt,
        @RequestPart("notice") @Valid NoticeCreateRequest request,
        @RequestPart("imageIds") List<UUID> imageIds,
        @RequestPart("images") List<MultipartFile> files) {
        return notices.create(UUID.fromString(jwt.getSubject()), request.title(), request.body(),
            request.bodyJson(), uploads(imageIds, files));
    }

    @Operation(summary = "본문 사진과 공지 수정", description = "본문 imageId는 기존 공지의 사진 또는 같은 요청의 새 사진만 허용합니다.")
    @ApiResponse(responseCode = "200", description = "공지 수정 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_NOTICE_CONTENT 또는 INVALID_REQUEST")
    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
    @ApiResponse(responseCode = "403", description = "ADMIN_ACCESS_DENIED")
    @ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "NOTICE_VERSION_CONFLICT")
    @ApiResponse(responseCode = "413", description = "요청 크기 초과")
    @ApiResponse(responseCode = "503", description = "NOTICE_IMAGE_STORAGE_UNAVAILABLE")
    @PutMapping(value = "/api/v1/admin/notices/{noticeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public NoticeResponse updateRichNotice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID noticeId,
        @RequestPart("notice") @Valid NoticeUpdateRequest request,
        @RequestPart("imageIds") List<UUID> imageIds,
        @RequestPart("images") List<MultipartFile> files) {
        return notices.update(UUID.fromString(jwt.getSubject()), noticeId, request.title(), request.body(),
            request.published(), request.version(), request.bodyJson(), uploads(imageIds, files));
    }

    @Operation(summary = "관리자 공지 본문 사진 조회")
    @ApiResponse(responseCode = "200", description = "JPG/PNG 사진", content = {
        @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
        @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")) })
    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
    @ApiResponse(responseCode = "403", description = "ADMIN_ACCESS_DENIED")
    @ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND 또는 NOTICE_IMAGE_NOT_FOUND")
    @ApiResponse(responseCode = "503", description = "NOTICE_IMAGE_STORAGE_UNAVAILABLE")
    @GetMapping("/api/v1/admin/notices/{noticeId}/images/{imageId}")
    public ResponseEntity<byte[]> adminImage(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID noticeId, @PathVariable UUID imageId) {
        return imageResponse(notices.downloadAdminImage(UUID.fromString(jwt.getSubject()), noticeId, imageId));
    }

    @Operation(summary = "학생 공개 공지 본문 사진 조회")
    @ApiResponse(responseCode = "200", description = "JPG/PNG 사진", content = {
        @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
        @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")) })
    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
    @ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND, NOTICE_NOT_FOUND 또는 NOTICE_IMAGE_NOT_FOUND")
    @ApiResponse(responseCode = "503", description = "NOTICE_IMAGE_STORAGE_UNAVAILABLE")
    @GetMapping("/api/v1/notices/{noticeId}/images/{imageId}")
    public ResponseEntity<byte[]> publishedImage(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID noticeId, @PathVariable UUID imageId) {
        return imageResponse(notices.downloadPublishedImage(UUID.fromString(jwt.getSubject()), noticeId, imageId));
    }

    private static ResponseEntity<byte[]> imageResponse(InquiryAttachmentFile file) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
            .cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .header("X-Content-Type-Options", "nosniff").body(file.content());
    }

    private static List<NoticeImageService.Upload> uploads(List<UUID> ids, List<MultipartFile> files) {
        if (ids.size() != files.size() || files.size() > 10 ||
            files.stream().mapToLong(MultipartFile::getSize).sum() > 5L * 1024 * 1024) {
            throw NoticeException.invalidContent();
        }
        var uploads = new ArrayList<NoticeImageService.Upload>();
        try {
            for (int index = 0; index < files.size(); index++) {
                var file = files.get(index);
                uploads.add(new NoticeImageService.Upload(ids.get(index), file.getOriginalFilename(),
                    file.getContentType(), file.getBytes()));
            }
        } catch (IOException exception) {
            throw NoticeException.invalidContent();
        }
        return uploads;
    }

    public record NoticeCreateRequest(
        @NotBlank @Size(max = 150) @Schema(example = "수강신청 안내") String title,
        @NotNull @Size(max = 10000) @Schema(example = "수강신청 일정을 확인해 주세요.") String body,
        @Schema(types = {"object", "null"}) JsonNode bodyJson
    ) {}

    public record NoticeUpdateRequest(
        @NotBlank @Size(max = 150) @Schema(example = "수강신청 안내") String title,
        @NotNull @Size(max = 10000) @Schema(example = "수강신청 일정을 확인해 주세요.") String body,
        @NotNull Boolean published,
        @NotNull @PositiveOrZero Long version,
        @Schema(types = {"object", "null"}) JsonNode bodyJson
    ) {}
}
