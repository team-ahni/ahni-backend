package com.ahni.backend.dto;

public record InquiryAttachmentFile(
    String filename,
    String contentType,
    byte[] content
) { }
