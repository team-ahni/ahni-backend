package com.ahni.backend.exception;

public class InquiryAttachmentTooLargeException extends RuntimeException {
    public InquiryAttachmentTooLargeException() {
        super("첨부파일은 5MB 이하만 등록할 수 있습니다.");
    }
}
