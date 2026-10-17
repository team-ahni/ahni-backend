package com.ahni.backend.exception;

public class InquiryAttachmentStorageUnavailableException extends RuntimeException {
    public InquiryAttachmentStorageUnavailableException() {
        super("첨부파일 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }
}
