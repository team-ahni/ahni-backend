package com.ahni.backend.entity;

import com.ahni.backend.domain.AccountStatus;
import com.ahni.backend.domain.EnrollmentStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Getter
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID entityId = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private UUID authUserId;

    @Column(length = 100)
    private String nickname;

    @Column(length = 320, nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private Integer admissionYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private EnrollmentStatus enrollmentStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    private static final int MIN_ADMISSION_YEAR = 2000;
    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    protected Student() { }

    public Student(
        UUID authUserId,
        String email,
        int admissionYear,
        EnrollmentStatus enrollmentStatus,
        String nickname
    ) {
        validateAdmissionYear(admissionYear);

        this.authUserId = authUserId;
        this.email = email;
        this.admissionYear = admissionYear;
        this.enrollmentStatus = enrollmentStatus;
        this.nickname = nickname;
    }

    /** 관리자가 본인 확인을 마친 재연결 요청에서만 호출한다. 이메일만으로 재연결하지 않는다. */
    public void relinkAuthUser(UUID authUserId) {
        if (authUserId == null) {
            throw new IllegalArgumentException("새 계정 식별자는 필수입니다.");
        }
        this.authUserId = authUserId;
    }

    private static void validateAdmissionYear(int admissionYear) {
        int currentYear = Year.now(SERVICE_ZONE_ID).getValue();

        if (admissionYear < MIN_ADMISSION_YEAR || admissionYear > currentYear) {
            throw new IllegalArgumentException(String.format("입학연도는 %d년부터 %d년 사이여야 합니다.", MIN_ADMISSION_YEAR, currentYear));
        }
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
