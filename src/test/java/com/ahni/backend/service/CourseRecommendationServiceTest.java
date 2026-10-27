package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.domain.CurriculumDivision;
import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.domain.RecommendedTerm;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Curriculum;
import com.ahni.backend.entity.CurriculumCourse;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.Lecture;
import com.ahni.backend.entity.MajorType;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentMajor;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.repository.CurriculumCourseRepository;
import com.ahni.backend.repository.GraduationRequirementRepository;
import com.ahni.backend.repository.LectureRepository;
import com.ahni.backend.repository.RequiredCourseRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRecommendationServiceTest {
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentMajorRepository studentMajorRepository;
    @Mock
    private StudentGradeRepository gradeRepository;
    @Mock
    private CurriculumCourseRepository curriculumCourseRepository;
    @Mock
    private GraduationRequirementRepository graduationRequirementRepository;
    @Mock
    private RequiredCourseRepository requiredCourseRepository;
    @Mock
    private LectureRepository lectureRepository;

    @InjectMocks
    private CourseRecommendationService service;

    private final UUID authUserId = UUID.randomUUID();
    private final Student student = new Student(authUserId, "student@inha.edu", 2026, EnrollmentStatus.ENROLLED, null);
    private final Department primary = new Department("소프트웨어융합공학과");
    private final Department doubleMajor = new Department("컴퓨터공학과");

    @Test
    void 계절학기와_범위를_벗어난_연도는_조회하지_않는다() {
        assertThatThrownBy(() -> service.recommend(authUserId, 2026, AcademicTerm.SUMMER))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recommend(authUserId, 1999, AcademicTerm.FIRST))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(studentRepository);
    }

    @Test
    void 학생_프로필이_없으면_404로_처리한다() {
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recommend(authUserId, 2026, AcademicTerm.FIRST))
            .isInstanceOf(StudentNotFoundException.class);
    }

    @Test
    void 앞_전공에서_추천한_과목은_다음_전공에서_빼고_교과과정이_없는_전공을_표시한다() {
        Course shared = course(null, "GEB1001");
        Course majorOnly = course(primary, "ITC1001");
        Curriculum primaryCurriculum = new Curriculum(primary, 2026, "자료", null);
        Curriculum doubleCurriculum = new Curriculum(doubleMajor, 2026, "자료", null);
        Department minor = new Department("수학과");
        givenStudent(List.of(
            new StudentMajor(student, minor, MajorType.MINOR),
            new StudentMajor(student, doubleMajor, MajorType.DOUBLE_MAJOR),
            new StudentMajor(student, primary, MajorType.PRIMARY)
        ));
        when(curriculumCourseRepository.findPublishedRecognitionLinks(any(), any())).thenReturn(List.of(
            link(primaryCurriculum, shared, CurriculumDivision.GENERAL_REQUIRED),
            link(primaryCurriculum, majorOnly, CurriculumDivision.MAJOR_REQUIRED),
            link(doubleCurriculum, shared, CurriculumDivision.GENERAL_REQUIRED)
        ));
        when(lectureRepository.findAllByAcademicYearAndTermOrderByCourseCodeAscSectionAsc(2026, AcademicTerm.FIRST))
            .thenReturn(List.of());

        var result = service.recommend(authUserId, 2026, AcademicTerm.FIRST);

        assertThat(result.semesterNumber()).isEqualTo(1);
        assertThat(result.lectureDataAvailable()).isFalse();
        assertThat(result.majors()).extracting(major -> major.majorType())
            .containsExactly("PRIMARY", "DOUBLE_MAJOR", "MINOR");
        assertThat(result.majors().get(0).courses()).extracting(course -> course.course().code())
            .containsExactly("GEB1001", "ITC1001");
        assertThat(result.majors().get(1).curriculumAvailable()).isTrue();
        assertThat(result.majors().get(1).courses()).isEmpty();
        assertThat(result.majors().get(2).curriculumAvailable()).isFalse();
    }

    @Test
    void 개설_강의가_있으면_개설된_과목만_추천한다() {
        Course offered = course(primary, "ITC1001");
        Course notOffered = course(primary, "ITC1002");
        Curriculum curriculum = new Curriculum(primary, 2026, "자료", null);
        givenStudent(List.of(new StudentMajor(student, primary, MajorType.PRIMARY)));
        when(curriculumCourseRepository.findPublishedRecognitionLinks(List.of(primary.getEntityId()), List.of(2026)))
            .thenReturn(List.of(
                link(curriculum, offered, CurriculumDivision.MAJOR_REQUIRED),
                link(curriculum, notOffered, CurriculumDivision.MAJOR_REQUIRED)
            ));
        when(lectureRepository.findAllByAcademicYearAndTermOrderByCourseCodeAscSectionAsc(2026, AcademicTerm.FIRST))
            .thenReturn(List.of(new Lecture(offered, "김교수", 2026, AcademicTerm.FIRST, "001")));

        var result = service.recommend(authUserId, 2026, AcademicTerm.FIRST);

        assertThat(result.lectureDataAvailable()).isTrue();
        assertThat(result.majors().getFirst().courses()).singleElement().satisfies(course -> {
            assertThat(course.course().code()).isEqualTo("ITC1001");
            assertThat(course.lectures()).extracting(lecture -> lecture.section()).containsExactly("001");
        });
    }

    private void givenStudent(List<StudentMajor> majors) {
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findAllByStudent(student)).thenReturn(List.of());
        when(studentMajorRepository.findAllByStudentAndDeletedAtIsNull(student)).thenReturn(majors);
        when(graduationRequirementRepository.findByDepartmentAndAdmissionYearAndMajorType(any(), any(Integer.class), any()))
            .thenReturn(Optional.empty());
    }

    private static Course course(Department department, String code) {
        return new Course(department, code, code + " 과목", new BigDecimal("3.0"),
            department == null ? CourseCategory.GENERAL_EDUCATION : CourseCategory.MAJOR);
    }

    private static CurriculumCourse link(Curriculum curriculum, Course course, CurriculumDivision division) {
        return new CurriculumCourse(curriculum, course, division, 1, RecommendedTerm.FIRST, null, null, null, null);
    }
}
