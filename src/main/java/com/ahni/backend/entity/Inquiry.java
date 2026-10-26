package com.ahni.backend.entity;

import com.ahni.backend.domain.InquiryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inquiry")
@Getter
public class Inquiry {
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_CONTENT_LENGTH = 4000;
    private static final int MAX_ANSWER_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID entityId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(length = MAX_TITLE_LENGTH, nullable = false)
    private String title;

    @Column(length = MAX_CONTENT_LENGTH, nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private InquiryStatus status = InquiryStatus.SUBMITTED;

    @Column(length = MAX_ANSWER_LENGTH)
    private String answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by_admin_id")
    private Admin answeredByAdmin;

    private Instant answeredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_admin_id")
    private Admin assignedAdmin;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    protected Inquiry() { }

    public Inquiry(Student student, String title, String content) {
        if (student == null) {
            throw new IllegalArgumentException("학생은 필수입니다.");
        }
        this.student = student;
        this.title = requireText(title, MAX_TITLE_LENGTH, "제목");
        this.content = requireText(content, MAX_CONTENT_LENGTH, "내용");
    }

    public void update(String title, String content) {
        ensureEditable();
        this.title = requireText(title, MAX_TITLE_LENGTH, "제목");
        this.content = requireText(content, MAX_CONTENT_LENGTH, "내용");
    }

    public void ensureEditable() {
        if (status == InquiryStatus.ANSWERED || status == InquiryStatus.CLOSED) {
            throw new IllegalStateException("답변이 등록된 문의는 수정할 수 없습니다.");
        }
    }

    public void delete() {
        this.deletedAt = Instant.now();
    }

    public void markInReview() {
        if (status == InquiryStatus.SUBMITTED) {
            this.status = InquiryStatus.IN_REVIEW;
        }
    }

    public void assign(Admin admin) {
        this.assignedAdmin = admin;
    }

    public void answer(Admin admin, String answer) {
        if (admin == null) {
            throw new IllegalArgumentException("답변 관리자는 필수입니다.");
        }
        this.answer = requireText(answer, MAX_ANSWER_LENGTH, "답변");
        this.answeredByAdmin = admin;
        this.answeredAt = Instant.now();
        this.status = InquiryStatus.ANSWERED;
    }

    private static String requireText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "은 필수입니다.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + "이 너무 깁니다.");
        }
        return trimmed;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
