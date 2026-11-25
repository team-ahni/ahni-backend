package com.ahni.backend.exception;

import org.springframework.http.HttpStatus;

public class NoticeException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    private NoticeException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static NoticeException notFound() {
        return new NoticeException(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND", "공지를 찾을 수 없습니다.");
    }

    public static NoticeException conflict() {
        return new NoticeException(HttpStatus.CONFLICT, "NOTICE_VERSION_CONFLICT", "공지가 변경되었습니다. 목록을 새로고침해 주세요.");
    }

    public static NoticeException invalidContent() {
        return new NoticeException(HttpStatus.BAD_REQUEST, "INVALID_NOTICE_CONTENT", "공지 내용과 사진 형식을 확인해 주세요.");
    }

    public static NoticeException imageNotFound() {
        return new NoticeException(HttpStatus.NOT_FOUND, "NOTICE_IMAGE_NOT_FOUND", "공지 사진을 찾을 수 없습니다.");
    }

    public static NoticeException storageUnavailable() {
        return new NoticeException(HttpStatus.SERVICE_UNAVAILABLE, "NOTICE_IMAGE_STORAGE_UNAVAILABLE", "사진 저장소에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
