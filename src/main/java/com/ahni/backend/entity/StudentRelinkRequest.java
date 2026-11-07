package com.ahni.backend.entity;

import com.ahni.backend.domain.StudentRelinkStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_relink_request")
@Getter
public class StudentRelinkRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID entityId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    @Column(nullable = false, updatable = false)
    private UUID newAuthUserId;

    @Column(length = 320, nullable = false, updatable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private StudentRelinkStatus status = StudentRelinkStatus.PENDING;

    private UUID previousAuthUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_admin_id")
    private Admin resolvedByAdmin;

    private Instant resolvedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected StudentRelinkRequest() { }

    public StudentRelinkRequest(Student student, UUID newAuthUserId, String email) {
        if (student == null || newAuthUserId == null || email == null || email.isBlank()) {
            throw new IllegalArgumentException("학생, 새 계정 식별자, 이메일은 필수입니다.");
        }
        this.student = student;
        this.newAuthUserId = newAuthUserId;
        this.email = email;
    }

    public boolean isPending() {
        return status == StudentRelinkStatus.PENDING;
    }

    public void approve(Admin admin) {
        resolve(admin, StudentRelinkStatus.APPROVED);
        this.previousAuthUserId = student.getAuthUserId();
        student.relinkAuthUser(newAuthUserId);
    }

    public void reject(Admin admin) {
        resolve(admin, StudentRelinkStatus.REJECTED);
    }

    private void resolve(Admin admin, StudentRelinkStatus status) {
        if (admin == null) {
            throw new IllegalArgumentException("처리 관리자는 필수입니다.");
        }
        if (!isPending()) {
            throw new IllegalStateException("이미 처리된 재연결 요청입니다.");
        }
        this.status = status;
        this.resolvedByAdmin = admin;
        this.resolvedAt = Instant.now();
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
