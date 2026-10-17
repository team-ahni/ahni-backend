package com.ahni.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inquiry_attachment")
@Getter
public class InquiryAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID entityId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inquiry_id", nullable = false, updatable = false)
    private Inquiry inquiry;

    @Column(length = 200, nullable = false, unique = true, updatable = false)
    private String storageKey;

    @Column(length = 255, nullable = false, updatable = false)
    private String filename;

    @Column(length = 100, nullable = false, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected InquiryAttachment() { }

    public InquiryAttachment(Inquiry inquiry, String filename, String contentType, long sizeBytes) {
        if (inquiry == null) {
            throw new IllegalArgumentException("문의는 필수입니다.");
        }
        this.inquiry = inquiry;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        // 원본 파일명은 저장 경로에 넣지 않는다.
        this.storageKey = "inquiries/" + inquiry.getEntityId() + "/" + entityId;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
