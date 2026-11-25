package com.ahni.backend.service;

import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.dto.CreditProgressResponse;
import com.ahni.backend.dto.GraduationCreditProgressResponse;
import com.ahni.backend.entity.GraduationRequirement;
import com.ahni.backend.entity.CurriculumCourse;
import com.ahni.backend.entity.StudentGrade;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

final class GraduationProgressCalculator {
    private GraduationProgressCalculator() { }

    static GraduationCreditProgressResponse calculate(
        GraduationRequirement requirement,
        List<StudentGrade> grades,
        List<CurriculumCourse> recognitionLinks
    ) {
        BigDecimal totalCompleted = GradeSummaryCalculator.calculate(grades)
            .completedCredits();
        UUID departmentEntityId = requirement.getDepartment().getEntityId();
        boolean separateFoundation = requirement.getMinFoundationCredit().signum() > 0;
        List<CurriculumCourse> departmentLinks = recognitionLinks.stream()
            .filter(link -> link.getCurriculum().isPublished())
            .filter(link -> link.getCurriculum().getDepartment().getDeletedAt() == null)
            .filter(link -> departmentEntityId.equals(link.getCurriculum().getDepartment().getEntityId()))
            .toList();
        Set<AnnualCourse> foundations = departmentLinks.stream()
            .filter(link -> link.getDivision() == com.ahni.backend.domain.CurriculumDivision.MAJOR_FOUNDATION)
            .map(link -> new AnnualCourse(link.getCurriculum().getCurriculumYear(), link.getCourse().getEntityId()))
            .collect(Collectors.toSet());
        Set<AnnualCourse> recognized = departmentLinks.stream()
            .filter(link -> link.getCurriculum().isPublished())
            .filter(link -> link.getCurriculum().getDepartment().getDeletedAt() == null)
            .filter(link -> departmentEntityId.equals(link.getCurriculum().getDepartment().getEntityId()))
            .filter(link -> switch (link.getDivision()) {
                case MAJOR_REQUIRED, MAJOR_ELECTIVE, MAJOR_FOUNDATION -> true;
                default -> false;
            })
            .map(link -> new AnnualCourse(link.getCurriculum().getCurriculumYear(), link.getCourse().getEntityId()))
            .collect(Collectors.toSet());
        List<StudentGrade> effectiveGrades = GradeSummaryCalculator.effectiveAttempts(grades);
        BigDecimal foundationCompleted = completedCredits(effectiveGrades.stream()
            .filter(grade -> foundations.contains(new AnnualCourse(grade.getAcademicYear(), grade.getCourse().getEntityId())))
            .toList());
        BigDecimal departmentCompleted = completedCredits(effectiveGrades.stream()
            .filter(grade -> grade.getCourse().getCategory() == CourseCategory.MAJOR)
            .filter(grade -> recognized.contains(new AnnualCourse(grade.getAcademicYear(), grade.getCourse().getEntityId())))
            .filter(grade -> !separateFoundation || !foundations.contains(new AnnualCourse(grade.getAcademicYear(), grade.getCourse().getEntityId())))
            .toList());
        BigDecimal generalCompleted = completedCredits(effectiveGrades.stream()
            .filter(grade -> grade.getCourse().getCategory()
                == CourseCategory.GENERAL_EDUCATION)
            .toList());

        return new GraduationCreditProgressResponse(
            progress(requirement.getMinTotalCredit(), totalCompleted),
            progress(requirement.getMinDepartmentCredit(), departmentCompleted),
            progress(requirement.getMinGeneralCredit(), generalCompleted),
            progress(requirement.getMinFoundationCredit(), separateFoundation ? foundationCompleted : BigDecimal.ZERO)
        );
    }

    private record AnnualCourse(int year, UUID courseEntityId) { }

    private static BigDecimal completedCredits(List<StudentGrade> grades) {
        return GradeSummaryCalculator.calculate(grades).completedCredits();
    }

    private static CreditProgressResponse progress(
        BigDecimal required,
        BigDecimal completed
    ) {
        BigDecimal remaining = required.subtract(completed).max(BigDecimal.ZERO)
            .setScale(1);
        return new CreditProgressResponse(
            required,
            completed,
            remaining,
            completed.compareTo(required) >= 0
        );
    }
}
