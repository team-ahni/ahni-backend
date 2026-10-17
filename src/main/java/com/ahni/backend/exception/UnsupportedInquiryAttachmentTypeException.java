package com.ahni.backend.exception;

public class UnsupportedInquiryAttachmentTypeException extends RuntimeException {
    public UnsupportedInquiryAttachmentTypeException() {
        super("JPG, PNG, PDF 파일만 첨부할 수 있습니다.");
    }
}
