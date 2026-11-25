package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.domain.GradeCode;
import com.ahni.backend.domain.CurriculumDivision;
import com.ahni.backend.entity.Curriculum;
import com.ahni.backend.entity.CurriculumCourse;
import com.ahni.backend.dto.GraduationCreditProgressResponse;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.GraduationRequirement;
import com.ahni.backend.entity.MajorType;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentGrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GraduationProgressCalculatorTest {
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
    void 전체_전공_교양_학점의_충족도를_계산한다() {
        GraduationRequirement requirement = requirement("6.0", "3.0", "2.0");
        List<StudentGrade> grades = List.of(
            grade(majorCourse(department, "CSE101"), GradeCode.A_PLUS, "3.0"),
            grade(generalCourse("GED101"), GradeCode.P, "2.0"),
            rpl(electiveCourse("ELE101"), "2.0")
        );

        GraduationCreditProgressResponse result = GraduationProgressCalculator
            .calculate(requirement, grades, List.of(link(department, 2025, grades.getFirst().getCourse(), true)));

        assertThat(result.total().required()).isEqualByComparingTo("6.0");
        assertThat(result.total().completed()).isEqualByComparingTo("7.0");
        assertThat(result.total().remaining()).isEqualByComparingTo("0.0");
        assertThat(result.total().met()).isTrue();
        assertThat(result.department().completed()).isEqualByComparingTo("3.0");
        assertThat(result.department().met()).isTrue();
        assertThat(result.general().completed()).isEqualByComparingTo("2.0");
        assertThat(result.general().met()).isTrue();
    }

    @Test
    void 대체된_성적과_미이수_성적은_제외하고_학과를_구분한다() {
        GraduationRequirement requirement = requirement("10.0", "6.0", "2.0");
        Course retakenCourse = majorCourse(department, "CSE201");
        StudentGrade original = grade(
            retakenCourse,
            2024,
            AcademicTerm.FIRST,
            GradeCode.A_PLUS,
            null
        );
        StudentGrade retake = grade(
            retakenCourse,
            2025,
            AcademicTerm.FIRST,
            GradeCode.F,
            original
        );
        Department otherDepartment = new Department("금융투자학과");

        GraduationCreditProgressResponse result = GraduationProgressCalculator
            .calculate(requirement, List.of(
                original,
                retake,
                grade(
                    majorCourse(otherDepartment, "FIN101"),
                    GradeCode.A_ZERO,
                    "3.0"
                ),
                grade(generalCourse("GED201"), GradeCode.NP, "2.0")
            ), List.of(link(department, 2024, retakenCourse, true)));

        assertThat(result.total().completed()).isEqualByComparingTo("3.0");
        assertThat(result.total().remaining()).isEqualByComparingTo("7.0");
        assertThat(result.department().completed()).isEqualByComparingTo("0.0");
        assertThat(result.department().remaining()).isEqualByComparingTo("6.0");
        assertThat(result.general().completed()).isEqualByComparingTo("0.0");
        assertThat(result.general().remaining()).isEqualByComparingTo("2.0");
        assertThat(result.total().met()).isFalse();
    }

    @Test
    void 다른_관리_학과의_과목도_공개된_수강연도_연결로_인정한다() {
        Course shared = majorCourse(new Department("관리학과"), "MTH1901");
        var grades = List.of(grade(shared, GradeCode.P, "2.0"));
        var result = GraduationProgressCalculator.calculate(requirement("3.0", "3.0", "0.0"),
            grades, List.of(link(department, 2025, shared, true),
                link(new Department("또다른인정학과"), 2025, shared, true)));
        assertThat(result.department().completed()).isEqualByComparingTo("2.0");
        assertThat(result.total().completed()).isEqualByComparingTo("2.0");
    }

    @Test
    void 관리_학과만_같거나_연도와_학과가_엇갈리거나_초안이면_전공으로_인정하지_않는다() {
        Course course = majorCourse(department, "MTH1901");
        var grades = List.of(grade(course, GradeCode.A_PLUS, "3.0"));
        for (var links : List.of(List.<CurriculumCourse>of(),
            List.of(link(department, 2024, course, true)),
            List.of(link(department, 2025, course, false)),
            List.of(link(department, 2024, course, true),
                link(new Department("다른학과"), 2025, course, true)))) {
            var result = GraduationProgressCalculator.calculate(requirement("3.0", "3.0", "0.0"), grades, links);
            assertThat(result.department().completed()).isEqualByComparingTo("0.0");
            assertThat(result.total().completed()).isEqualByComparingTo("3.0");
        }
    }

    @Test
    void 학과가_없는_공통전공의_RPL도_성적에_저장된_학점으로_인정한다() {
        Course course = majorCourse(null, "MTH1902");
        var result = GraduationProgressCalculator.calculate(requirement("3.0", "3.0", "0.0"),
            List.of(rpl(course, "2.0")), List.of(link(department, 2025, course, true)));
        assertThat(result.department().completed()).isEqualByComparingTo("2.0");
    }

    @Test
    void 재수강연도_연결이_없어도_실패한_재수강이_이전_이수를_되살리지_않는다() {
        Course course = majorCourse(department, "RETAKE101");
        StudentGrade original = grade(course, 2024, AcademicTerm.FIRST, GradeCode.A_PLUS, null);
        StudentGrade retake = grade(course, 2025, AcademicTerm.FIRST, GradeCode.F, original);
        var result = GraduationProgressCalculator.calculate(requirement("3.0", "3.0", "0.0"),
            List.of(original, retake), List.of(link(department, 2024, course, true)));
        assertThat(result.total().completed()).isEqualByComparingTo("0.0");
        assertThat(result.department().completed()).isEqualByComparingTo("0.0");
    }

    @Test
    void foundationCreditIsSeparateFromMajorAndCountedOnceInTotal() {
        var requirement = requirement("126", "65", "25");
        requirement.setMinFoundationCredit(new BigDecimal("15"));
        var foundation = majorCourse(department, "FND101");
        var major = majorCourse(department, "MAJ101");
        var foundationLink = link(department, 2025, foundation, true);
        var curriculum = new Curriculum(department, 2025, "공식 자료", null);
        curriculum.setPublished(true);
        var majorLink = new CurriculumCourse(curriculum, major, CurriculumDivision.MAJOR_REQUIRED,
            null, null, null, null, null, null);
        var result = GraduationProgressCalculator.calculate(requirement,
            List.of(grade(foundation, GradeCode.P, "3"), grade(major, GradeCode.A_ZERO, "3")),
            List.of(foundationLink, majorLink));
        assertThat(result.total().completed()).isEqualByComparingTo("6");
        assertThat(result.department().completed()).isEqualByComparingTo("3");
        assertThat(result.foundation().completed()).isEqualByComparingTo("3");
        assertThat(result.foundation().remaining()).isEqualByComparingTo("12");
        assertThat(result.foundation().met()).isFalse();
        assertThat(result.general().completed()).isEqualByComparingTo("0");
    }

    private CurriculumCourse link(Department recognizedDepartment, int year, Course course, boolean published) {
        Curriculum curriculum = new Curriculum(recognizedDepartment, year, "공식 교과과정", null);
        curriculum.setPublished(published);
        return new CurriculumCourse(curriculum, course, CurriculumDivision.MAJOR_FOUNDATION,
            null, null, null, null, null, null);
    }

    private GraduationRequirement requirement(
        String total,
        String departmentCredit,
        String general
    ) {
        return new GraduationRequirement(
            department,
            2024,
            MajorType.PRIMARY,
            new BigDecimal(total),
            new BigDecimal(departmentCredit),
            new BigDecimal(general),
            "2024학년도 졸업요건",
            null
        );
    }

    private Course majorCourse(Department owner, String code) {
        return new Course(
            owner,
            code,
            code,
            new BigDecimal("3.0"),
            CourseCategory.MAJOR
        );
    }

    private Course generalCourse(String code) {
        return new Course(
            null,
            code,
            code,
            new BigDecimal("3.0"),
            CourseCategory.GENERAL_EDUCATION
        );
    }

    private Course electiveCourse(String code) {
        return new Course(
            null,
            code,
            code,
            new BigDecimal("3.0"),
            CourseCategory.ELECTIVE
        );
    }

    private StudentGrade grade(Course course, GradeCode gradeCode, String credit) {
        return grade(
            course,
            2025,
            AcademicTerm.SECOND,
            gradeCode,
            null,
            credit
        );
    }

    private StudentGrade grade(
        Course course,
        int academicYear,
        AcademicTerm term,
        GradeCode gradeCode,
        StudentGrade replacedGrade
    ) {
        return grade(
            course,
            academicYear,
            term,
            gradeCode,
            replacedGrade,
            "3.0"
        );
    }

    private StudentGrade grade(
        Course course,
        int academicYear,
        AcademicTerm term,
        GradeCode gradeCode,
        StudentGrade replacedGrade,
        String credit
    ) {
        return new StudentGrade(
            student,
            course,
            academicYear,
            term,
            gradeCode,
            new BigDecimal(credit),
            false,
            replacedGrade
        );
    }

    private StudentGrade rpl(Course course, String credit) {
        return new StudentGrade(
            student,
            course,
            2025,
            AcademicTerm.SECOND,
            null,
            new BigDecimal(credit),
            true,
            null
        );
    }
}
