package com.ahni.backend.exception;

public class InquiryAttachmentLimitExceededException extends RuntimeException {
    public InquiryAttachmentLimitExceededException() {
        super("문의 하나에 첨부파일은 3개까지 등록할 수 있습니다.");
    }
}
