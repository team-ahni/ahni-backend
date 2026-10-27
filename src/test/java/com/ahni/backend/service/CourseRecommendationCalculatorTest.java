package com.ahni.backend.service;

import com.ahni.backend.domain.*;
import com.ahni.backend.dto.LectureResponse;
import com.ahni.backend.dto.RecommendedCourseResponse;
import com.ahni.backend.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.ahni.backend.domain.CourseRecommendationReason.*;
import static org.assertj.core.api.Assertions.assertThat;

class CourseRecommendationCalculatorTest {
    private final Department department = new Department("소프트웨어융합공학과");
    private final Curriculum curriculum = new Curriculum(department, 2026, "2026 교과과정", null);
    private final Student student = new Student(UUID.randomUUID(), "student@inha.edu", 2024, EnrollmentStatus.ENROLLED, null);

    @Test
    void 이전_정규학기_수로_추천_학기_번호를_계산한다() {
        Course course = course("ITC1001");
        List<StudentGrade> grades = List.of(
            grade(course, 2024, AcademicTerm.FIRST, GradeCode.A_PLUS),
            grade(course("ITC1002"), 2024, AcademicTerm.FIRST, GradeCode.A_PLUS),
            grade(course("ITC1003"), 2024, AcademicTerm.SUMMER, GradeCode.A_PLUS),
            grade(course("ITC1004"), 2024, AcademicTerm.SECOND, GradeCode.A_PLUS),
            grade(course("ITC1005"), 2026, AcademicTerm.FIRST, GradeCode.A_PLUS)
        );

        assertThat(CourseRecommendationCalculator.semesterNumber(grades, 2025, AcademicTerm.FIRST)).isEqualTo(3);
        assertThat(CourseRecommendationCalculator.semesterNumber(List.of(), 2025, AcademicTerm.FIRST)).isEqualTo(1);
    }

    @Test
    void 졸업필수_지난학기_이번학기_순서로_추천하고_이후_학기와_이수한_과목은_뺀다() {
        Course requiredOverdue = course("ITC2001");
        Course overdue = course("ITC2002");
        Course now = course("ITC3001");
        Course later = course("ITC4001");
        Course completed = course("ITC2003");
        Course generalElective = course("GED1001");
        List<CurriculumCourse> links = List.of(
            link(now, CurriculumDivision.MAJOR_ELECTIVE, 3, RecommendedTerm.FIRST),
            link(later, CurriculumDivision.MAJOR_REQUIRED, 4, RecommendedTerm.FIRST),
            link(overdue, CurriculumDivision.MAJOR_ELECTIVE, 2, RecommendedTerm.SECOND),
            link(requiredOverdue, CurriculumDivision.MAJOR_REQUIRED, 2, RecommendedTerm.FIRST),
            link(completed, CurriculumDivision.MAJOR_REQUIRED, 2, RecommendedTerm.FIRST),
            link(generalElective, CurriculumDivision.GENERAL_ELECTIVE, 1, RecommendedTerm.FIRST)
        );

        var result = CourseRecommendationCalculator.recommend(
            links,
            Set.of(requiredOverdue.getEntityId(), later.getEntityId()),
            List.of(grade(completed, 2025, AcademicTerm.FIRST, GradeCode.B_ZERO)),
            5,
            null
        );

        assertThat(result).extracting(r -> r.course().code()).containsExactly("ITC2001", "ITC2002", "ITC3001");
        assertThat(result.get(0).reasons()).containsExactly(GRADUATION_REQUIRED, OVERDUE);
        assertThat(result.get(1).reasons()).containsExactly(OVERDUE);
        assertThat(result.get(2).reasons()).containsExactly(RECOMMENDED_NOW);
        assertThat(result).allSatisfy(r -> assertThat(r.lectures()).isEmpty());
    }

    @Test
    void 낮은_등급과_F는_재수강으로_추천하고_최근_성적을_보여준다() {
        Course lowGrade = course("ITC2001");
        Course failedRequired = course("ITC2002");
        Course replaced = course("ITC2003");
        StudentGrade original = grade(replaced, 2024, AcademicTerm.FIRST, GradeCode.D_ZERO);
        StudentGrade retaken = new StudentGrade(student, replaced, 2025, AcademicTerm.FIRST, GradeCode.A_ZERO,
            new BigDecimal("3.0"), false, original);
        List<StudentGrade> grades = List.of(
            grade(lowGrade, 2024, AcademicTerm.FIRST, GradeCode.F),
            grade(lowGrade, 2025, AcademicTerm.FIRST, GradeCode.C_PLUS),
            grade(failedRequired, 2025, AcademicTerm.FIRST, GradeCode.F),
            original,
            retaken
        );

        var result = CourseRecommendationCalculator.recommend(
            List.of(
                link(lowGrade, CurriculumDivision.MAJOR_ELECTIVE, 4, RecommendedTerm.FIRST),
                link(failedRequired, CurriculumDivision.MAJOR_REQUIRED, 1, RecommendedTerm.FIRST),
                link(replaced, CurriculumDivision.MAJOR_REQUIRED, 1, RecommendedTerm.FIRST)
            ),
            Set.of(failedRequired.getEntityId()),
            GradeSummaryCalculator.effectiveAttempts(grades),
            5,
            null
        );

        assertThat(result).extracting(r -> r.course().code()).containsExactly("ITC2002", "ITC2001");
        assertThat(result.get(0).reasons()).containsExactly(GRADUATION_REQUIRED, OVERDUE, RETAKE);
        assertThat(result.get(0).previousGradeCode()).isEqualTo(GradeCode.F);
        assertThat(result.get(1).reasons()).containsExactly(RETAKE);
        assertThat(result.get(1).previousGradeCode()).isEqualTo(GradeCode.C_PLUS);
    }

    @Test
    void 개설_정보가_있으면_개설된_과목만_분반과_함께_추천한다() {
        Course offered = course("ITC3001");
        Course notOffered = course("ITC3002");
        LectureResponse lecture = new LectureResponse(UUID.randomUUID(), offered.getEntityId(), "ITC3001", "과목",
            new BigDecimal("3.0"), "김교수", 2026, AcademicTerm.FIRST, "001", List.of());

        var result = CourseRecommendationCalculator.recommend(
            List.of(
                link(offered, CurriculumDivision.MAJOR_ELECTIVE, 3, RecommendedTerm.BOTH),
                link(notOffered, CurriculumDivision.MAJOR_ELECTIVE, 3, RecommendedTerm.BOTH)
            ),
            Set.of(),
            List.of(),
            6,
            Map.of(offered.getEntityId(), List.of(lecture))
        );

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.reasons()).containsExactly(RECOMMENDED_NOW);
            assertThat(r.lectures()).containsExactly(lecture);
        });
    }

    @Test
    void 권장_학기가_없는_과목은_졸업_필수일_때만_추천한다() {
        Course required = course("GEB1001");
        Course optional = course("GEB1002");

        List<RecommendedCourseResponse> result = CourseRecommendationCalculator.recommend(
            List.of(
                link(required, CurriculumDivision.GENERAL_REQUIRED, null, null),
                link(optional, CurriculumDivision.GENERAL_REQUIRED, null, null)
            ),
            Set.of(required.getEntityId()),
            List.of(),
            1,
            null
        );

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.course().code()).isEqualTo("GEB1001");
            assertThat(r.reasons()).containsExactly(GRADUATION_REQUIRED);
        });
    }

    private Course course(String code) {
        return new Course(department, code, code + " 과목", new BigDecimal("3.0"), CourseCategory.MAJOR);
    }

    private CurriculumCourse link(Course course, CurriculumDivision division, Integer year, RecommendedTerm term) {
        return new CurriculumCourse(curriculum, course, division, year, term, null, null, null, null);
    }

    private StudentGrade grade(Course course, int year, AcademicTerm term, GradeCode code) {
        return new StudentGrade(student, course, year, term, code, new BigDecimal("3.0"), false, null);
    }
}
