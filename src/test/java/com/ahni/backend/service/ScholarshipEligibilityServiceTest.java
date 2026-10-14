package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.domain.GradeCode;
import com.ahni.backend.dto.ScholarshipEligibilityResponse;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.MajorType;
import com.ahni.backend.entity.ScholarshipCriteria;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentGrade;
import com.ahni.backend.entity.StudentMajor;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.repository.ScholarshipCriteriaRepository;
import com.ahni.backend.repository.StudentGradeRepository;
import com.ahni.backend.repository.StudentMajorRepository;
import com.ahni.backend.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScholarshipEligibilityServiceTest {
    private final UUID authUserId = UUID.randomUUID();
    private final Department primary = new Department("소프트웨어융합공학과");
    private final Department doubleMajor = new Department("컴퓨터공학과");
    private final Student student = new Student(
        authUserId, "student@inha.edu", 2024, EnrollmentStatus.ENROLLED, "인하"
    );

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentMajorRepository studentMajorRepository;
    @Mock
    private StudentGradeRepository gradeRepository;
    @Mock
    private ScholarshipCriteriaRepository scholarshipCriteriaRepository;
    @InjectMocks
    private ScholarshipEligibilityService service;

    @Test
    void 주전공_학과와_입학연도의_장학_기준으로_판정한다() {
        StudentGrade grade = new StudentGrade(
            student,
            new Course(primary, "CSE101", "프로그래밍 기초", new BigDecimal("3.0"), CourseCategory.MAJOR),
            2024, AcademicTerm.FIRST, GradeCode.B_ZERO, new BigDecimal("3.0"), false, null
        );
        ScholarshipCriteria criteria = new ScholarshipCriteria(
            primary, 2024, "성적우수장학금", new BigDecimal("3.50"), new BigDecimal("12.0"), 0
        );
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(studentMajorRepository.findAllByStudentAndDeletedAtIsNull(student)).thenReturn(List.of(
            new StudentMajor(student, doubleMajor, MajorType.DOUBLE_MAJOR),
            new StudentMajor(student, primary, MajorType.PRIMARY)
        ));
        when(gradeRepository.findAllByStudent(student)).thenReturn(List.of(grade));
        when(scholarshipCriteriaRepository.findAllByDepartmentAndAdmissionYearOrderByNameAsc(primary, 2024))
            .thenReturn(List.of(criteria));

        ScholarshipEligibilityResponse response = service.getEligibility(authUserId);

        assertThat(response.department().entityId()).isEqualTo(primary.getEntityId());
        assertThat(response.admissionYear()).isEqualTo(2024);
        assertThat(response.gpa()).isEqualByComparingTo("3.00");
        assertThat(response.completedCredits()).isEqualByComparingTo("3.0");
        assertThat(response.warningCount()).isZero();
        assertThat(response.scholarships()).singleElement().satisfies(result -> {
            assertThat(result.criteriaEntityId()).isEqualTo(criteria.getEntityId());
            assertThat(result.gpaShortfall()).isEqualByComparingTo("0.50");
            assertThat(result.creditShortfall()).isEqualByComparingTo("9.0");
            assertThat(result.met()).isFalse();
        });
    }

    @Test
    void 등록된_기준이_없으면_빈_결과를_반환한다() {
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(studentMajorRepository.findAllByStudentAndDeletedAtIsNull(student))
            .thenReturn(List.of(new StudentMajor(student, primary, MajorType.PRIMARY)));
        when(gradeRepository.findAllByStudent(student)).thenReturn(List.of());
        when(scholarshipCriteriaRepository.findAllByDepartmentAndAdmissionYearOrderByNameAsc(primary, 2024))
            .thenReturn(List.of());

        ScholarshipEligibilityResponse response = service.getEligibility(authUserId);

        assertThat(response.scholarships()).isEmpty();
        assertThat(response.gpa()).isEqualByComparingTo("0.00");
    }

    @Test
    void 학생_프로필이_없으면_판정할_수_없다() {
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEligibility(authUserId))
            .isInstanceOf(StudentNotFoundException.class);
    }
}
