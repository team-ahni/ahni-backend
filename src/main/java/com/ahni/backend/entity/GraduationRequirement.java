package com.ahni.backend.entity;

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
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
public class GraduationRequirement {
    private static final int MAX_SOURCE_TITLE_LENGTH = 200;
    private static final int MAX_SOURCE_URL_LENGTH = 2048;
    private static final int MIN_ADMISSION_YEAR = 2000;
    private static final int MAX_ADMISSION_YEAR = 9999;
    private static final BigDecimal MIN_CREDIT = BigDecimal.ZERO;
    private static final BigDecimal MAX_CREDIT = new BigDecimal("999.9");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID entityId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false)
    private Integer admissionYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private MajorType majorType;

    @Column(precision = 5, scale = 1, nullable = false)
    private BigDecimal minTotalCredit;

    @Column(precision = 5, scale = 1, nullable = false)
    private BigDecimal minDepartmentCredit;

    @Column(precision = 5, scale = 1, nullable = false)
    private BigDecimal minGeneralCredit;

    @Column(precision = 5, scale = 1, nullable = false)
    @ColumnDefault("0")
    private BigDecimal minFoundationCredit = BigDecimal.ZERO;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean evaluationComplete = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String policyDetails;

    public void setMinFoundationCredit(BigDecimal value) {
        this.minFoundationCredit = validateCredit(value);
    }

    public void markEvaluationIncomplete(String policyDetails) {
        this.evaluationComplete = false;
        this.policyDetails = policyDetails;
    }


    @Column(length = MAX_SOURCE_TITLE_LENGTH, nullable = false)
    private String sourceTitle;

    @Column(length = MAX_SOURCE_URL_LENGTH)
    private String sourceUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected GraduationRequirement() { }

    public GraduationRequirement(
        Department department,
        int admissionYear,
        MajorType majorType,
        BigDecimal minTotalCredit,
        BigDecimal minDepartmentCredit,
        BigDecimal minGeneralCredit,
        String sourceTitle,
        String sourceUrl
    ) {
        if (department == null) {
            throw new IllegalArgumentException("졸업요건의 학과는 필수입니다.");
        }
        if (admissionYear < MIN_ADMISSION_YEAR || admissionYear > MAX_ADMISSION_YEAR) {
            throw new IllegalArgumentException("입학연도는 2000년부터 9999년 사이여야 합니다.");
        }
        if (majorType == null) {
            throw new IllegalArgumentException("전공 유형은 필수입니다.");
        }

        this.department = department;
        this.admissionYear = admissionYear;
        this.majorType = majorType;
        this.minTotalCredit = validateCredit(minTotalCredit);
        this.minDepartmentCredit = validateCredit(minDepartmentCredit);
        this.minGeneralCredit = validateCredit(minGeneralCredit);
        this.sourceTitle = normalizeSourceTitle(sourceTitle);
        this.sourceUrl = normalizeSourceUrl(sourceUrl);
    }

    public void update(
        BigDecimal minTotalCredit,
        BigDecimal minDepartmentCredit,
        BigDecimal minGeneralCredit,
        String sourceTitle,
        String sourceUrl
    ) {
        this.minTotalCredit = validateCredit(minTotalCredit);
        this.minDepartmentCredit = validateCredit(minDepartmentCredit);
        this.minGeneralCredit = validateCredit(minGeneralCredit);
        this.sourceTitle = normalizeSourceTitle(sourceTitle);
        this.sourceUrl = normalizeSourceUrl(sourceUrl);
    }

    private static BigDecimal validateCredit(BigDecimal credit) {
        if (credit == null) {
            throw new IllegalArgumentException("졸업요건 학점은 필수입니다.");
        }
        if (credit.compareTo(MIN_CREDIT) < 0 || credit.compareTo(MAX_CREDIT) > 0) {
            throw new IllegalArgumentException("졸업요건 학점은 0부터 999.9 사이여야 합니다.");
        }
        if (credit.stripTrailingZeros().scale() > 1) {
            throw new IllegalArgumentException("졸업요건 학점은 소수점 한 자리까지만 입력할 수 있습니다.");
        }
        return credit;
    }

    private static String normalizeSourceTitle(String sourceTitle) {
        if (sourceTitle == null || sourceTitle.isBlank()) {
            throw new IllegalArgumentException("졸업요건 출처명은 필수입니다.");
        }
        String normalized = sourceTitle.trim();
        if (normalized.length() > MAX_SOURCE_TITLE_LENGTH) {
            throw new IllegalArgumentException("졸업요건 출처명은 200자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    private static String normalizeSourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        String normalized = sourceUrl.trim();
        if (normalized.length() > MAX_SOURCE_URL_LENGTH) {
            throw new IllegalArgumentException("졸업요건 출처 URL은 2048자를 초과할 수 없습니다.");
        }
        return normalized;
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
