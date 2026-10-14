package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.domain.GradeCode;
import com.ahni.backend.dto.GradeSummaryResponse;
import com.ahni.backend.dto.ScholarshipResultResponse;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.ScholarshipCriteria;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentGrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScholarshipEligibilityCalculatorTest {
    private Student student;
    private Department department;

    @BeforeEach
    void setUp() {
        student = new Student(
            UUID.randomUUID(),
            "student@inha.edu",
            2024,
            EnrollmentStatus.ENROLLED,
            "인하"
        );
        department = new Department("소프트웨어융합공학과");
    }

    @Test
    void 정규학기_평점이_1점70_미만인_학기마다_학사경고로_센다() {
        List<StudentGrade> grades = List.of(
            grade("CSE101", 2024, AcademicTerm.FIRST, GradeCode.D_ZERO, "3.0"),
            grade("CSE102", 2024, AcademicTerm.FIRST, GradeCode.F, "3.0"),
            grade("CSE103", 2024, AcademicTerm.SECOND, GradeCode.A_PLUS, "3.0"),
            grade("CSE104", 2025, AcademicTerm.FIRST, GradeCode.D_PLUS, "3.0")
        );

        assertThat(ScholarshipEligibilityCalculator.warningCount(grades)).isEqualTo(2);
    }

    @Test
    void 학기_평점이_정확히_1점70이면_학사경고가_아니다() {
        List<StudentGrade> grades = List.of(
            grade("CSE101", 2024, AcademicTerm.FIRST, GradeCode.D_PLUS, "3.0"),
            grade("CSE102", 2024, AcademicTerm.FIRST, GradeCode.C_ZERO, "2.0")
        );

        assertThat(ScholarshipEligibilityCalculator.warningCount(grades)).isZero();
    }

    @Test
    void 계절학기와_평점_학점이_없는_학기는_학사경고를_만들지_않는다() {
        List<StudentGrade> grades = List.of(
            grade("CSE101", 2024, AcademicTerm.SUMMER, GradeCode.F, "3.0"),
            grade("CSE102", 2024, AcademicTerm.WINTER, GradeCode.F, "3.0"),
            grade("CSE103", 2025, AcademicTerm.FIRST, GradeCode.NP, "1.0"),
            grade("CSE104", 2025, AcademicTerm.FIRST, GradeCode.P, "1.0")
        );

        assertThat(ScholarshipEligibilityCalculator.warningCount(grades)).isZero();
    }

    @Test
    void 이후_재수강으로_대체된_성적도_해당_학기_학사경고에_포함한다() {
        Course course = course("CSE101");
        StudentGrade failed = new StudentGrade(
            student, course, 2024, AcademicTerm.FIRST, GradeCode.F,
            new BigDecimal("3.0"), false, null
        );
        StudentGrade retake = new StudentGrade(
            student, course, 2025, AcademicTerm.FIRST, GradeCode.A_PLUS,
            new BigDecimal("3.0"), false, failed
        );

        assertThat(ScholarshipEligibilityCalculator.warningCount(List.of(failed, retake)))
            .isEqualTo(1);
    }

    @Test
    void 장학금별_부족분과_충족_여부를_계산한다() {
        ScholarshipCriteria excellence = criteria("성적우수장학금", "3.50", "12.0", 0);
        ScholarshipCriteria support = criteria("생활지원장학금", "2.00", "12.0", 1);
        GradeSummaryResponse summary = new GradeSummaryResponse(
            new BigDecimal("3.20"),
            new BigDecimal("15.0"),
            new BigDecimal("15.0"),
            List.of()
        );

        List<ScholarshipResultResponse> results = ScholarshipEligibilityCalculator
            .evaluate(List.of(excellence, support), summary, 1);

        ScholarshipResultResponse excellenceResult = results.getFirst();
        assertThat(excellenceResult.name()).isEqualTo("성적우수장학금");
        assertThat(excellenceResult.gpaShortfall()).isEqualByComparingTo("0.30");
        assertThat(excellenceResult.creditShortfall()).isEqualByComparingTo("0.0");
        assertThat(excellenceResult.excessWarnings()).isEqualTo(1);
        assertThat(excellenceResult.met()).isFalse();

        ScholarshipResultResponse supportResult = results.get(1);
        assertThat(supportResult.gpaShortfall()).isEqualByComparingTo("0.00");
        assertThat(supportResult.excessWarnings()).isZero();
        assertThat(supportResult.met()).isTrue();
    }

    @Test
    void 이수학점이_부족하면_부족한_학점을_반환한다() {
        ScholarshipCriteria criteria = criteria("성적우수장학금", "3.00", "12.0", 0);
        GradeSummaryResponse summary = new GradeSummaryResponse(
            new BigDecimal("4.00"),
            new BigDecimal("9.0"),
            new BigDecimal("9.0"),
            List.of()
        );

        ScholarshipResultResponse result = ScholarshipEligibilityCalculator
            .evaluate(List.of(criteria), summary, 0).getFirst();

        assertThat(result.creditShortfall()).isEqualByComparingTo("3.0");
        assertThat(result.met()).isFalse();
    }

    private StudentGrade grade(
        String code,
        int academicYear,
        AcademicTerm term,
        GradeCode gradeCode,
        String credit
    ) {
        return new StudentGrade(
            student, course(code), academicYear, term, gradeCode,
            new BigDecimal(credit), false, null
        );
    }

    private Course course(String code) {
        return new Course(department, code, code, new BigDecimal("3.0"), CourseCategory.MAJOR);
    }

    private ScholarshipCriteria criteria(
        String name,
        String minGpa,
        String minCredits,
        int maxWarnings
    ) {
        return new ScholarshipCriteria(
            department, 2024, name,
            new BigDecimal(minGpa), new BigDecimal(minCredits), maxWarnings
        );
    }
}
