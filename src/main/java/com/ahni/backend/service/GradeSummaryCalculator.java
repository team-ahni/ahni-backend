package com.ahni.backend.service;

import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.domain.GradeCode;
import com.ahni.backend.dto.GradeCategorySummaryResponse;
import com.ahni.backend.dto.GradeSummaryResponse;
import com.ahni.backend.dto.ExpectedGradeRequest;
import com.ahni.backend.entity.StudentGrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class GradeSummaryCalculator {
    private GradeSummaryCalculator() { }

    static GradeSummaryResponse calculate(List<StudentGrade> grades) {
        return summarizeAll(actualValues(effectiveAttempts(grades)));
    }

    // 학기 단위 판정(학사경고)은 이후 재수강으로 대체된 성적도 그 학기 기록으로 유지한다.
    static GradeSummaryResponse calculateIncludingReplaced(List<StudentGrade> grades) {
        return summarizeAll(actualValues(grades));
    }

    static GradeSummaryResponse project(List<StudentGrade> grades, List<ExpectedGradeRequest> expectedGrades) {
        return summarizeAll(Stream.concat(actualValues(effectiveAttempts(grades)).stream(), expectedGrades.stream()
            .map(grade -> new CalculationGrade(grade.category(), grade.credit(),
                grade.gradeCode(), grade.gradeCode().gradePoint(), false))).toList());
    }

    private static List<CalculationGrade> actualValues(List<StudentGrade> grades) {
        return grades.stream()
            .map(grade -> new CalculationGrade(grade.getCourse().getCategory(), grade.getCredit(),
                grade.getGradeCode(), grade.getGradePoint(), grade.isRpl())).toList();
    }

    private static GradeSummaryResponse summarizeAll(List<CalculationGrade> activeGrades) {
        SummaryValues total = summarize(activeGrades);
        List<GradeCategorySummaryResponse> categories = Arrays.stream(CourseCategory.values()).map(category -> {
            SummaryValues values = summarize(activeGrades.stream().filter(grade -> grade.category() == category).toList());
            return new GradeCategorySummaryResponse(category, values.gpa(), values.completedCredits(), values.gpaCredits());
        }).toList();
        return new GradeSummaryResponse(total.gpa(), total.completedCredits(), total.gpaCredits(), categories);
    }

    static List<StudentGrade> effectiveAttempts(List<StudentGrade> grades) {
        Set<UUID> replacedGradeIds = grades.stream()
            .map(StudentGrade::getReplacedGrade)
            .filter(replacedGrade -> replacedGrade != null)
            .map(StudentGrade::getEntityId)
            .collect(Collectors.toSet());
        return grades.stream()
            .filter(grade -> !replacedGradeIds.contains(grade.getEntityId()))
            .toList();

    }

    private static SummaryValues summarize(List<CalculationGrade> grades) {
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal completedCredits = BigDecimal.ZERO;
        BigDecimal gpaCredits = BigDecimal.ZERO;

        for (CalculationGrade grade : grades) {
            BigDecimal credit = grade.credit();
            if (grade.rpl()) {
                completedCredits = completedCredits.add(credit);
                continue;
            }

            switch (grade.gradeCode()) {
                case P -> completedCredits = completedCredits.add(credit);
                case NP -> { }
                case F -> gpaCredits = gpaCredits.add(credit);
                default -> {
                    completedCredits = completedCredits.add(credit);
                    gpaCredits = gpaCredits.add(credit);
                    numerator = numerator.add(
                        credit.multiply(grade.gradePoint())
                    );
                }
            }
        }

        return new SummaryValues(
            gpa(numerator, gpaCredits),
            completedCredits.setScale(1),
            gpaCredits.setScale(1)
        );
    }

    private static BigDecimal gpa(
        BigDecimal numerator,
        BigDecimal gpaCredits
    ) {
        if (gpaCredits.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return numerator.divide(gpaCredits, 2, RoundingMode.HALF_UP);
    }

    private record CalculationGrade(CourseCategory category, BigDecimal credit,
        GradeCode gradeCode, BigDecimal gradePoint, boolean rpl) { }

    private record SummaryValues(
        BigDecimal gpa,
        BigDecimal completedCredits,
        BigDecimal gpaCredits
    ) { }
}
