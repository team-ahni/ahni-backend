package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.domain.CourseRecommendationReason;
import com.ahni.backend.domain.CurriculumDivision;
import com.ahni.backend.domain.GradeCode;
import com.ahni.backend.domain.RecommendedTerm;
import com.ahni.backend.dto.CourseResponse;
import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.LectureResponse;
import com.ahni.backend.dto.RecommendedCourseResponse;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.CurriculumCourse;
import com.ahni.backend.entity.StudentGrade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class CourseRecommendationCalculator {
    // ponytail: 인하대 재수강 허용 기준(C+ 이하)을 상수로 둔다. 학칙이 바뀌거나 다른 학교를 지원하면 설정으로 옮긴다.
    private static final Set<GradeCode> RETAKE_GRADES = EnumSet.of(
        GradeCode.C_PLUS, GradeCode.C_ZERO, GradeCode.D_PLUS, GradeCode.D_ZERO, GradeCode.F, GradeCode.NP);
    private static final Set<CurriculumDivision> RECOMMENDED_DIVISIONS = EnumSet.of(
        CurriculumDivision.MAJOR_REQUIRED, CurriculumDivision.MAJOR_ELECTIVE,
        CurriculumDivision.MAJOR_FOUNDATION, CurriculumDivision.GENERAL_REQUIRED);
    private static final Comparator<StudentGrade> LATEST_FIRST = Comparator
        .comparingInt(StudentGrade::getAcademicYear)
        .thenComparingInt(grade -> grade.getTerm().sequence())
        .reversed();

    private CourseRecommendationCalculator() { }

    static int semesterNumber(List<StudentGrade> grades, int academicYear, AcademicTerm term) {
        int target = academicYear * 10 + term.sequence();
        return (int) grades.stream()
            .filter(grade -> grade.getTerm() == AcademicTerm.FIRST || grade.getTerm() == AcademicTerm.SECOND)
            .mapToInt(grade -> grade.getAcademicYear() * 10 + grade.getTerm().sequence())
            .filter(key -> key < target)
            .distinct()
            .count() + 1;
    }

    /**
     * @param effectiveAttempts 재수강으로 대체된 성적을 뺀 성적
     * @param lecturesByCourse  과목별 개설 분반. 개설 정보가 없는 학기면 null이고 개설 여부로 거르지 않는다.
     */
    static List<RecommendedCourseResponse> recommend(
        List<CurriculumCourse> links,
        Set<UUID> graduationRequiredCourseIds,
        List<StudentGrade> effectiveAttempts,
        int semesterNumber,
        Map<UUID, List<LectureResponse>> lecturesByCourse
    ) {
        return links.stream()
            .filter(link -> RECOMMENDED_DIVISIONS.contains(link.getDivision()))
            .filter(link -> lecturesByCourse == null || lecturesByCourse.containsKey(link.getCourse().getEntityId()))
            .map(link -> recommend(link, graduationRequiredCourseIds, effectiveAttempts, semesterNumber, lecturesByCourse))
            .filter(recommendation -> !recommendation.reasons().isEmpty())
            .sorted(Comparator
                .comparing((RecommendedCourseResponse recommendation) -> recommendation.reasons().getFirst())
                .thenComparing(RecommendedCourseResponse::recommendedYear, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(recommendation -> recommendation.course().code()))
            .toList();
    }

    private static RecommendedCourseResponse recommend(
        CurriculumCourse link,
        Set<UUID> graduationRequiredCourseIds,
        List<StudentGrade> effectiveAttempts,
        int semesterNumber,
        Map<UUID, List<LectureResponse>> lecturesByCourse
    ) {
        Course course = link.getCourse();
        List<StudentGrade> attempts = effectiveAttempts.stream()
            .filter(grade -> grade.getCourse().getEntityId().equals(course.getEntityId()))
            .sorted(LATEST_FIRST)
            .toList();
        boolean completed = attempts.stream()
            .anyMatch(grade -> grade.isRpl() || (grade.getGradeCode() != GradeCode.F && grade.getGradeCode() != GradeCode.NP));
        boolean wellCompleted = attempts.stream()
            .anyMatch(grade -> grade.isRpl() || !RETAKE_GRADES.contains(grade.getGradeCode()));

        List<CourseRecommendationReason> reasons = new ArrayList<>();
        if (!wellCompleted) {
            Integer year = link.getRecommendedYear();
            RecommendedTerm term = link.getRecommendedTerm();
            boolean hasPosition = year != null && term != null;
            int first = hasPosition ? (year - 1) * 2 + (term == RecommendedTerm.SECOND ? 2 : 1) : 0;
            int last = hasPosition ? (year - 1) * 2 + (term == RecommendedTerm.FIRST ? 1 : 2) : 0;
            boolean required = !completed && graduationRequiredCourseIds.contains(course.getEntityId());
            boolean overdue = hasPosition && last < semesterNumber;
            boolean now = hasPosition && first <= semesterNumber && semesterNumber <= last;
            if (required && (overdue || now || !hasPosition)) reasons.add(CourseRecommendationReason.GRADUATION_REQUIRED);
            if (overdue && !completed) reasons.add(CourseRecommendationReason.OVERDUE);
            if (now && !completed) reasons.add(CourseRecommendationReason.RECOMMENDED_NOW);
            if (!attempts.isEmpty()) reasons.add(CourseRecommendationReason.RETAKE);
        }
        return new RecommendedCourseResponse(
            courseResponse(course),
            link.getDivision(),
            link.getRecommendedYear(),
            link.getRecommendedTerm(),
            reasons,
            reasons.contains(CourseRecommendationReason.RETAKE) ? attempts.getFirst().getGradeCode() : null,
            lecturesByCourse == null ? List.of() : lecturesByCourse.get(course.getEntityId())
        );
    }

    private static CourseResponse courseResponse(Course course) {
        var department = course.getDepartment();
        return new CourseResponse(course.getEntityId(), course.getCode(), course.getName(), course.getCredit(),
            course.getCategory(), department == null ? null : new DepartmentResponse(department.getEntityId(), department.getName()));
    }
}
