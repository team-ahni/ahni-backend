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

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
