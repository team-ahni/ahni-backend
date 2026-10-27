package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.domain.GradeCode;
import com.ahni.backend.dto.GradeRegistrationRequest;
import com.ahni.backend.dto.GradeResponse;
import com.ahni.backend.dto.GradeSummaryResponse;
import com.ahni.backend.dto.GradeUpdateRequest;
import com.ahni.backend.dto.ExpectedGradeRequest;
import com.ahni.backend.dto.GradeSimulationRequest;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentGrade;
import com.ahni.backend.exception.CourseNotFoundException;
import com.ahni.backend.exception.GradeAlreadyRegisteredException;
import com.ahni.backend.exception.GradeReplacementConflictException;
import com.ahni.backend.exception.GradeNotFoundException;
import com.ahni.backend.exception.InvalidGradeException;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.repository.StudentGradeRepository;
import com.ahni.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeServiceTest {
    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CurriculumService courseRepository;

    @Mock
    private StudentGradeRepository gradeRepository;

    private GradeService gradeService;

    private UUID authUserId;
    private Student student;
    private Department department;
    private Course course;

    @BeforeEach
    void setUp() {
        gradeService = new GradeService(
            studentRepository,
            courseRepository,
            gradeRepository
        );
        authUserId = UUID.randomUUID();
        student = new Student(
            authUserId,
            "student@inha.edu",
            2024,
            EnrollmentStatus.ENROLLED,
            "인하"
        );
        department = new Department("소프트웨어융합공학과");
        course = majorCourse(department, "CSE101", "프로그래밍 기초");
    }

    @Test
    void 인증된_학생의_성적을_등록한다() {
        stubRegistrationTarget(course);
        when(gradeRepository.saveAndFlush(any(StudentGrade.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        GradeResponse result = gradeService.register(authUserId, request(
            course,
            2025,
            AcademicTerm.SECOND,
            GradeCode.A_PLUS,
            new BigDecimal("3.0"),
            false,
            null
        ));

        assertThat(result.gradePoint()).isEqualByComparingTo("4.50");
        assertThat(result.credit()).isEqualByComparingTo("3.0");
        assertThat(result.course().code()).isEqualTo("CSE101");
        assertThat(result.course().department().name())
            .isEqualTo("소프트웨어융합공학과");
    }

    @Test
    void RPL은_등급과_평점을_null로_반환한다() {
        stubRegistrationTarget(course);
        when(gradeRepository.saveAndFlush(any(StudentGrade.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        GradeResponse result = gradeService.register(authUserId, request(
            course,
            2025,
            AcademicTerm.FIRST,
            null,
            new BigDecimal("3.0"),
            true,
            null
        ));

        assertThat(result.gradeCode()).isNull();
        assertThat(result.gradePoint()).isNull();
        assertThat(result.rpl()).isTrue();
    }

    @Test
    void 이전_성적을_연결해_재수강_성적을_등록한다() {
        StudentGrade previous = grade(
            student,
            course,
            2024,
            AcademicTerm.SECOND
        );
        stubRegistrationTarget(course);
        when(gradeRepository.findByEntityIdAndStudent(
            previous.getEntityId(),
            student
        )).thenReturn(Optional.of(previous));
        when(gradeRepository.saveAndFlush(any(StudentGrade.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        GradeResponse result = gradeService.register(
            authUserId,
            new GradeRegistrationRequest(
                course.getEntityId(),
                2025,
                AcademicTerm.FIRST,
                GradeCode.A_ZERO,
                new BigDecimal("3.0"),
                false,
                previous.getEntityId()
            )
        );

        assertThat(result.replacedGradeEntityId())
            .isEqualTo(previous.getEntityId());
    }

    @Test
    void 이미_대체된_성적은_다른_재수강에_연결할_수_없다() {
        StudentGrade previous = grade(
            student,
            course,
            2024,
            AcademicTerm.SECOND
        );
        stubRegistrationTarget(course);
        when(gradeRepository.findByEntityIdAndStudent(
            previous.getEntityId(),
            student
        )).thenReturn(Optional.of(previous));
        when(gradeRepository.existsByReplacedGrade(previous)).thenReturn(true);

        assertThatThrownBy(() -> gradeService.register(
            authUserId,
            new GradeRegistrationRequest(
                course.getEntityId(),
                2025,
                AcademicTerm.FIRST,
                GradeCode.A_ZERO,
                new BigDecimal("3.0"),
                false,
                previous.getEntityId()
            )
        )).isInstanceOf(GradeReplacementConflictException.class)
            .hasMessage("이미 다른 재수강 성적에 연결된 성적입니다.");

        verify(gradeRepository, never()).saveAndFlush(any(StudentGrade.class));
    }

    @Test
    void 학생이_없으면_과목을_조회하지_않는다() {
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gradeService.register(
            authUserId,
            standardRequest(course)
        )).isInstanceOf(StudentNotFoundException.class);

        verifyNoInteractions(courseRepository, gradeRepository);
    }

    @Test
    void 존재하지_않는_과목에는_성적을_등록할_수_없다() {
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(courseRepository.requireOfferedCourse(anyInt(), any(), eq(course.getEntityId())))
            .thenThrow(new CourseNotFoundException());

        assertThatThrownBy(() -> gradeService.register(
            authUserId,
            standardRequest(course)
        )).isInstanceOf(CourseNotFoundException.class);

        verifyNoInteractions(gradeRepository);
    }

    @Test
    void 같은_학기의_과목_성적이_있으면_저장하지_않는다() {
        stubRegistrationTarget(course);
        when(gradeRepository.existsByStudentAndCourseAndAcademicYearAndTerm(
            student,
            course,
            2025,
            AcademicTerm.SECOND
        )).thenReturn(true);

        assertThatThrownBy(() -> gradeService.register(
            authUserId,
            standardRequest(course)
        )).isInstanceOf(GradeAlreadyRegisteredException.class);

        verify(gradeRepository, never()).saveAndFlush(any(StudentGrade.class));
    }

    @Test
    void 도메인_검증_오류를_성적_입력_오류로_변환한다() {
        stubRegistrationTarget(course);

        assertThatThrownBy(() -> gradeService.register(authUserId, request(
            course,
            1999,
            AcademicTerm.SECOND,
            GradeCode.A_PLUS,
            new BigDecimal("3.0"),
            false,
            null
        )))
            .isInstanceOf(InvalidGradeException.class)
            .hasMessage("수강연도가 올바르지 않습니다.");
    }

    @Test
    void 동시_등록으로_발생한_무결성_오류를_중복_오류로_변환한다() {
        stubRegistrationTarget(course);
        when(gradeRepository.saveAndFlush(any(StudentGrade.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> gradeService.register(
            authUserId,
            standardRequest(course)
        )).isInstanceOf(GradeAlreadyRegisteredException.class);
    }

    @Test
    void 인증된_학생의_성적만_조회한다() {
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findAllByStudent(student)).thenReturn(List.of(
            grade(student, course, 2025, AcademicTerm.SECOND)
        ));

        List<GradeResponse> result = gradeService.getGrades(authUserId);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.course().entityId()).isEqualTo(course.getEntityId());
            assertThat(item.gradeCode()).isEqualTo(GradeCode.A_PLUS);
        });
        verify(gradeRepository).findAllByStudent(student);
    }

    @Test
    void 학과가_없는_과목은_학과_정보를_null로_반환한다() {
        Course generalCourse = new Course(
            null,
            "GE101",
            "대학 글쓰기",
            new BigDecimal("2.0"),
            CourseCategory.GENERAL_EDUCATION
        );
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findAllByStudent(student)).thenReturn(List.of(
            grade(student, generalCourse, 2025, AcademicTerm.FIRST)
        ));

        GradeResponse result = gradeService.getGrades(authUserId).getFirst();

        assertThat(result.course().department()).isNull();
    }

    @Test
    void 최신_연도와_학기_순으로_정렬하고_같은_학기는_과목코드순으로_정렬한다() {
        Course laterCode = majorCourse(department, "CSE201", "자료구조");
        Course earlierCode = majorCourse(department, "CSE101", "프로그래밍 기초");
        Course older = majorCourse(department, "CSE001", "컴퓨터 개론");
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findAllByStudent(student)).thenReturn(List.of(
            grade(student, older, 2024, AcademicTerm.WINTER),
            grade(student, laterCode, 2025, AcademicTerm.SECOND),
            grade(student, earlierCode, 2025, AcademicTerm.SECOND),
            grade(student, earlierCode, 2025, AcademicTerm.FIRST)
        ));

        List<GradeResponse> result = gradeService.getGrades(authUserId);

        assertThat(result)
            .extracting(item -> item.academicYear() + "-" + item.term() + "-" + item.course().code())
            .containsExactly(
                "2025-SECOND-CSE101",
                "2025-SECOND-CSE201",
                "2025-FIRST-CSE101",
                "2024-WINTER-CSE001"
            );
    }

    @Test
    void 등록된_성적이_없으면_빈_목록을_반환한다() {
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findAllByStudent(student)).thenReturn(List.of());

        assertThat(gradeService.getGrades(authUserId)).isEmpty();
    }

    @Test
    void 인증된_학생의_성적만으로_GPA_요약을_계산한다() {
        when(studentRepository.findByAuthUserId(authUserId))
            .thenReturn(Optional.of(student));
        when(gradeRepository.findAllByStudent(student)).thenReturn(List.of(
            grade(student, course, 2025, AcademicTerm.SECOND)
        ));

        GradeSummaryResponse result = gradeService.getSummary(authUserId);

        assertThat(result.gpa()).isEqualByComparingTo("4.50");
        assertThat(result.completedCredits()).isEqualByComparingTo("3.0");
        assertThat(result.categories()).hasSize(3);
        verify(gradeRepository).findAllByStudent(student);
    }

    @Test
    void 인증된_학생이_자신의_성적을_수정한다() {
        UUID gradeEntityId = UUID.randomUUID();
        StudentGrade grade = grade(student, course, 2025, AcademicTerm.SECOND);
        GradeUpdateRequest request = updateRequest(
            2024,
            AcademicTerm.WINTER,
            GradeCode.B_PLUS,
            new BigDecimal("2.0"),
            false,
            null
        );
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findByEntityIdAndStudent(gradeEntityId, student))
            .thenReturn(Optional.of(grade));
        when(gradeRepository.saveAndFlush(grade)).thenReturn(grade);

        GradeResponse result = gradeService.update(authUserId, gradeEntityId, request);

        assertThat(result.academicYear()).isEqualTo(2024);
        assertThat(result.term()).isEqualTo(AcademicTerm.WINTER);
        assertThat(result.gradeCode()).isEqualTo(GradeCode.B_PLUS);
        assertThat(result.gradePoint()).isEqualByComparingTo("3.50");
        assertThat(result.credit()).isEqualByComparingTo("2.0");
        assertThat(result.replacedGradeEntityId()).isNull();
    }

    @Test
    void 성적_수정으로_이전_성적과_재수강_관계를_설정한다() {
        UUID gradeEntityId = UUID.randomUUID();
        StudentGrade previous = grade(
            student,
            course,
            2024,
            AcademicTerm.SECOND
        );
        StudentGrade grade = grade(student, course, 2025, AcademicTerm.SECOND);
        GradeUpdateRequest request = updateRequest(
            2025,
            AcademicTerm.SECOND,
            GradeCode.A_PLUS,
            new BigDecimal("3.0"),
            false,
            previous.getEntityId()
        );
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findByEntityIdAndStudent(gradeEntityId, student))
            .thenReturn(Optional.of(grade));
        when(gradeRepository.findByEntityIdAndStudent(previous.getEntityId(), student))
            .thenReturn(Optional.of(previous));
        when(gradeRepository.saveAndFlush(grade)).thenReturn(grade);

        GradeResponse result = gradeService.update(authUserId, gradeEntityId, request);

        assertThat(result.replacedGradeEntityId())
            .isEqualTo(previous.getEntityId());
        verify(gradeRepository).existsByReplacedGradeAndEntityIdNot(
            previous,
            grade.getEntityId()
        );
    }

    @Test
    void 다른_성적과_수강정보가_중복되면_수정하지_않는다() {
        UUID gradeEntityId = UUID.randomUUID();
        StudentGrade grade = grade(student, course, 2025, AcademicTerm.FIRST);
        GradeUpdateRequest request = updateRequest(
            2025,
            AcademicTerm.SECOND,
            GradeCode.A_PLUS,
            new BigDecimal("3.0"),
            false,
            null
        );
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findByEntityIdAndStudent(gradeEntityId, student))
            .thenReturn(Optional.of(grade));
        when(gradeRepository.existsByStudentAndCourseAndAcademicYearAndTermAndIdNot(
            student,
            course,
            2025,
            AcademicTerm.SECOND,
            grade.getId()
        )).thenReturn(true);

        assertThatThrownBy(() -> gradeService.update(authUserId, gradeEntityId, request))
            .isInstanceOf(GradeAlreadyRegisteredException.class);

        verify(gradeRepository, never()).saveAndFlush(grade);
    }

    @Test
    void 유효하지_않은_성적_수정은_입력_오류로_변환한다() {
        UUID gradeEntityId = UUID.randomUUID();
        StudentGrade grade = grade(student, course, 2025, AcademicTerm.SECOND);
        GradeUpdateRequest request = updateRequest(
            1999,
            AcademicTerm.SECOND,
            GradeCode.A_PLUS,
            new BigDecimal("3.0"),
            false,
            null
        );
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findByEntityIdAndStudent(gradeEntityId, student))
            .thenReturn(Optional.of(grade));

        assertThatThrownBy(() -> gradeService.update(authUserId, gradeEntityId, request))
            .isInstanceOf(InvalidGradeException.class)
            .hasMessage("수강연도가 올바르지 않습니다.");
    }

    @Test
    void 재수강으로_대체된_성적은_후속_재수강보다_늦게_수정할_수_없다() {
        UUID gradeEntityId = UUID.randomUUID();
        StudentGrade previous = grade(
            student,
            course,
            2024,
            AcademicTerm.SECOND
        );
        StudentGrade successor = new StudentGrade(
            student,
            course,
            2025,
            AcademicTerm.FIRST,
            GradeCode.A_PLUS,
            new BigDecimal("3.0"),
            false,
            previous
        );
        GradeUpdateRequest request = updateRequest(
            2025,
            AcademicTerm.SECOND,
            GradeCode.B_PLUS,
            new BigDecimal("3.0"),
            false,
            null
        );
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findByEntityIdAndStudent(gradeEntityId, student))
            .thenReturn(Optional.of(previous));
        when(gradeRepository.findByReplacedGrade(previous))
            .thenReturn(Optional.of(successor));

        assertThatThrownBy(() -> gradeService.update(authUserId, gradeEntityId, request))
            .isInstanceOf(InvalidGradeException.class)
            .hasMessage("재수강으로 대체된 성적은 후속 성적보다 이전 학기여야 합니다.");

        verify(gradeRepository, never()).saveAndFlush(previous);
    }

    @Test
    void 동시_수정의_무결성_오류를_중복_오류로_변환한다() {
        UUID gradeEntityId = UUID.randomUUID();
        StudentGrade grade = grade(student, course, 2025, AcademicTerm.FIRST);
        GradeUpdateRequest request = updateRequest(
            2025,
            AcademicTerm.SECOND,
            GradeCode.A_PLUS,
            new BigDecimal("3.0"),
            false,
            null
        );
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findByEntityIdAndStudent(gradeEntityId, student))
            .thenReturn(Optional.of(grade));
        when(gradeRepository.saveAndFlush(grade))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> gradeService.update(authUserId, gradeEntityId, request))
            .isInstanceOf(GradeAlreadyRegisteredException.class);
    }

    @Test
    void 없거나_다른_학생의_성적은_수정할_수_없다() {
        UUID gradeEntityId = UUID.randomUUID();
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findByEntityIdAndStudent(gradeEntityId, student))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> gradeService.update(
            authUserId,
            gradeEntityId,
            updateRequest(
                2025,
                AcademicTerm.SECOND,
                GradeCode.A_PLUS,
                new BigDecimal("3.0"),
                false,
                null
            )
        )).isInstanceOf(GradeNotFoundException.class);
    }

    @Test
    void 인증된_학생이_자신의_성적을_삭제한다() {
        UUID gradeEntityId = UUID.randomUUID();
        StudentGrade grade = grade(student, course, 2025, AcademicTerm.SECOND);
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findByEntityIdAndStudent(gradeEntityId, student))
            .thenReturn(Optional.of(grade));

        gradeService.delete(authUserId, gradeEntityId);

        verify(gradeRepository).delete(grade);
    }

    @Test
    void 재수강으로_대체된_이전_성적은_삭제할_수_없다() {
        UUID gradeEntityId = UUID.randomUUID();
        StudentGrade grade = grade(student, course, 2024, AcademicTerm.SECOND);
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findByEntityIdAndStudent(gradeEntityId, student))
            .thenReturn(Optional.of(grade));
        when(gradeRepository.existsByReplacedGrade(grade)).thenReturn(true);

        assertThatThrownBy(() -> gradeService.delete(authUserId, gradeEntityId))
            .isInstanceOf(GradeReplacementConflictException.class)
            .hasMessage("재수강으로 대체된 이전 성적은 삭제할 수 없습니다.");

        verify(gradeRepository, never()).delete(grade);
    }

    @Test
    void 없거나_다른_학생의_성적은_삭제할_수_없다() {
        UUID gradeEntityId = UUID.randomUUID();
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findByEntityIdAndStudent(gradeEntityId, student))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> gradeService.delete(authUserId, gradeEntityId))
            .isInstanceOf(GradeNotFoundException.class);

        verify(gradeRepository, never()).delete(any(StudentGrade.class));
    }

    @Test
    void 시뮬레이션은_인증된_학생의_현재와_예상_요약을_반환한다() {
        StudentGrade actual = grade(student, course, 2025, AcademicTerm.FIRST);
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(gradeRepository.findAllByStudent(student)).thenReturn(List.of(actual));
        var result = gradeService.simulate(authUserId, new GradeSimulationRequest(List.of(
            new ExpectedGradeRequest(CourseCategory.MAJOR, new BigDecimal("3.0"), GradeCode.B_PLUS)
        )));
        assertThat(result.current().gpa()).isEqualByComparingTo("4.50");
        assertThat(result.projected().gpa()).isEqualByComparingTo("4.00");
        assertThat(actual.getGradeCode()).isEqualTo(GradeCode.A_PLUS);
        assertThat(actual.getCredit()).isEqualByComparingTo("3.0");
        verify(gradeRepository, never()).saveAndFlush(any(StudentGrade.class));
        verifyNoInteractions(courseRepository);
    }

    @Test
    void 시뮬레이션도_학생_프로필이_필수다() {
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> gradeService.simulate(authUserId, new GradeSimulationRequest(List.of(
            new ExpectedGradeRequest(CourseCategory.MAJOR, new BigDecimal("3.0"), GradeCode.A_PLUS)
        )))).isInstanceOf(StudentNotFoundException.class);
        verifyNoInteractions(courseRepository, gradeRepository);
    }

    private void stubRegistrationTarget(Course targetCourse) {
        when(studentRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(courseRepository.requireOfferedCourse(anyInt(), any(), eq(targetCourse.getEntityId())))
            .thenReturn(targetCourse);
    }

    private GradeRegistrationRequest standardRequest(Course targetCourse) {
        return request(
            targetCourse,
            2025,
            AcademicTerm.SECOND,
            GradeCode.A_PLUS,
            new BigDecimal("3.0"),
            false,
            null
        );
    }

    private GradeRegistrationRequest request(
        Course targetCourse,
        int academicYear,
        AcademicTerm term,
        GradeCode gradeCode,
        BigDecimal credit,
        boolean rpl,
        UUID replacedGradeEntityId
    ) {
        return new GradeRegistrationRequest(
            targetCourse.getEntityId(),
            academicYear,
            term,
            gradeCode,
            credit,
            rpl,
            replacedGradeEntityId
        );
    }

    private GradeUpdateRequest updateRequest(
        int academicYear,
        AcademicTerm term,
        GradeCode gradeCode,
        BigDecimal credit,
        boolean rpl,
        UUID replacedGradeEntityId
    ) {
        return new GradeUpdateRequest(
            academicYear,
            term,
            gradeCode,
            credit,
            rpl,
            replacedGradeEntityId
        );
    }

    private StudentGrade grade(
        Student owner,
        Course targetCourse,
        int academicYear,
        AcademicTerm term
    ) {
        return new StudentGrade(
            owner,
            targetCourse,
            academicYear,
            term,
            GradeCode.A_PLUS,
            targetCourse.getCredit(),
            false,
            (StudentGrade) null
        );
    }

    private Course majorCourse(Department owner, String code, String name) {
        return new Course(
            owner,
            code,
            name,
            new BigDecimal("3.0"),
            CourseCategory.MAJOR
        );
    }
}
