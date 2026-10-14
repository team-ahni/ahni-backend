package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.dto.GradeSummaryResponse;
import com.ahni.backend.dto.ScholarshipResultResponse;
import com.ahni.backend.entity.ScholarshipCriteria;
import com.ahni.backend.entity.StudentGrade;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ScholarshipEligibilityCalculator {
    // ponytail: 인하대 학칙 값을 상수로 둔다. 다른 학교를 지원하면 기관별 설정으로 옮긴다.
    static final BigDecimal ACADEMIC_WARNING_GPA = new BigDecimal("1.70");

    private ScholarshipEligibilityCalculator() { }

    static int warningCount(List<StudentGrade> grades) {
        Map<String, List<StudentGrade>> regularTerms = grades.stream()
            .filter(grade -> grade.getTerm() == AcademicTerm.FIRST
                || grade.getTerm() == AcademicTerm.SECOND)
            .collect(Collectors.groupingBy(
                grade -> grade.getAcademicYear() + "-" + grade.getTerm()
            ));
        return (int) regularTerms.values().stream()
            .map(GradeSummaryCalculator::calculateIncludingReplaced)
            .filter(summary -> summary.gpaCredits().signum() > 0)
            .filter(summary -> summary.gpa().compareTo(ACADEMIC_WARNING_GPA) < 0)
            .count();
    }

    static List<ScholarshipResultResponse> evaluate(
        List<ScholarshipCriteria> criteria,
        GradeSummaryResponse summary,
        int warningCount
    ) {
        return criteria.stream()
            .map(item -> evaluate(item, summary, warningCount))
            .toList();
    }

    private static ScholarshipResultResponse evaluate(
        ScholarshipCriteria criteria,
        GradeSummaryResponse summary,
        int warningCount
    ) {
        BigDecimal gpaShortfall = shortfall(criteria.getMinGpa(), summary.gpa());
        BigDecimal creditShortfall = shortfall(
            criteria.getMinCredits(),
            summary.completedCredits()
        );
        int excessWarnings = Math.max(0, warningCount - criteria.getMaxWarnings());
        return new ScholarshipResultResponse(
            criteria.getEntityId(),
            criteria.getName(),
            criteria.getMinGpa(),
            criteria.getMinCredits(),
            criteria.getMaxWarnings(),
            gpaShortfall,
            creditShortfall,
            excessWarnings,
            gpaShortfall.signum() == 0 && creditShortfall.signum() == 0 && excessWarnings == 0
        );
    }

    private static BigDecimal shortfall(BigDecimal required, BigDecimal current) {
        return required.subtract(current).max(BigDecimal.ZERO.setScale(required.scale()));
    }
}
