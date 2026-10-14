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
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
public class ScholarshipCriteria {
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MIN_ADMISSION_YEAR = 2000;
    private static final int MAX_ADMISSION_YEAR = 9999;
    private static final BigDecimal MAX_GPA = new BigDecimal("4.50");
    private static final BigDecimal MAX_CREDITS = new BigDecimal("999.9");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID entityId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false, updatable = false)
    private Department department;

    @Column(nullable = false, updatable = false)
    private Integer admissionYear;

    @Column(length = MAX_NAME_LENGTH, nullable = false)
    private String name;

    @Column(precision = 3, scale = 2, nullable = false)
    private BigDecimal minGpa;

    @Column(precision = 4, scale = 1, nullable = false)
    private BigDecimal minCredits;

    @Column(nullable = false)
    private Integer maxWarnings;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ScholarshipCriteria() { }

    public ScholarshipCriteria(
        Department department,
        int admissionYear,
        String name,
        BigDecimal minGpa,
        BigDecimal minCredits,
        int maxWarnings
    ) {
        if (department == null) {
            throw new IllegalArgumentException("장학 기준의 학과는 필수입니다.");
        }
        if (admissionYear < MIN_ADMISSION_YEAR || admissionYear > MAX_ADMISSION_YEAR) {
            throw new IllegalArgumentException("입학연도는 2000년부터 9999년 사이여야 합니다.");
        }
        this.department = department;
        this.admissionYear = admissionYear;
        update(name, minGpa, minCredits, maxWarnings);
    }

    public void update(
        String name,
        BigDecimal minGpa,
        BigDecimal minCredits,
        int maxWarnings
    ) {
        this.name = normalizeName(name);
        this.minGpa = validateGpa(minGpa);
        this.minCredits = validateCredits(minCredits);
        if (maxWarnings < 0) {
            throw new IllegalArgumentException("허용 학사경고 횟수는 0 이상이어야 합니다.");
        }
        this.maxWarnings = maxWarnings;
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("장학금 이름은 필수입니다.");
        }
        String normalized = name.trim();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("장학금 이름은 100자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    private static BigDecimal validateGpa(BigDecimal gpa) {
        if (gpa == null || gpa.signum() < 0 || gpa.compareTo(MAX_GPA) > 0) {
            throw new IllegalArgumentException("최소 평점은 0.00부터 4.50 사이여야 합니다.");
        }
        if (gpa.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("최소 평점은 소수점 두 자리까지만 입력할 수 있습니다.");
        }
        return gpa;
    }

    private static BigDecimal validateCredits(BigDecimal credits) {
        if (credits == null || credits.signum() < 0 || credits.compareTo(MAX_CREDITS) > 0) {
            throw new IllegalArgumentException("최소 이수학점은 0부터 999.9 사이여야 합니다.");
        }
        if (credits.stripTrailingZeros().scale() > 1) {
            throw new IllegalArgumentException("최소 이수학점은 소수점 한 자리까지만 입력할 수 있습니다.");
        }
        return credits;
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
