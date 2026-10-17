package com.ahni.backend.exception;

public class InquiryAttachmentNotFoundException extends RuntimeException {
    public InquiryAttachmentNotFoundException() {
        super("첨부파일을 찾을 수 없습니다.");
    }
}
