package com.ahni.backend.repository;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.domain.GradeCode;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentGrade;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@SpringBootTest
@Transactional
@Tag("integration")
class StudentGradeRepositoryIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:18");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
            "spring.datasource.driver-class-name",
            POSTGRES::getDriverClassName
        );
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Autowired
    private StudentGradeRepository studentGradeRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearGradeData() {
        jdbcTemplate.update("DELETE FROM public.student_grade");
        jdbcTemplate.update("DELETE FROM public.course");
        jdbcTemplate.update("DELETE FROM public.student_major");
        jdbcTemplate.update("DELETE FROM public.student");
        jdbcTemplate.update("DELETE FROM public.department");
    }

    @Test
    void 재수강으로_연결된_성적을_학생별로_한번에_삭제한다() {
        GradeFixture owner = saveFixture("reset@inha.edu", "CSE101");
        GradeFixture other = saveFixture("other@inha.edu", "FIN101");
        StudentGrade original = studentGradeRepository.saveAndFlush(
            grade(owner.student(), owner.course(), AcademicTerm.FIRST)
        );
        studentGradeRepository.saveAndFlush(new StudentGrade(
            owner.student(), owner.course(), 2025, AcademicTerm.SECOND,
            GradeCode.A_PLUS, new BigDecimal("3.0"), false, original
        ));
        studentGradeRepository.saveAndFlush(grade(other.student(), other.course(), AcademicTerm.FIRST));

        studentGradeRepository.deleteAllForStudent(owner.student());
        studentGradeRepository.deleteAllForStudent(owner.student());

        assertThat(studentGradeRepository.findAllByStudent(owner.student())).isEmpty();
        assertThat(studentGradeRepository.findAllByStudent(other.student())).hasSize(1);
    }

    @Test
    void 성적을_저장하면_외부식별자와_기록시간이_생성된다() {
        GradeFixture fixture = saveFixture("saved@inha.edu", "CSE101");

        StudentGrade saved = studentGradeRepository.saveAndFlush(grade(
            fixture.student(),
            fixture.course(),
            AcademicTerm.SECOND
        ));

        assertThat(saved.getEntityId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(studentGradeRepository
            .existsByStudentAndCourseAndAcademicYearAndTerm(
                fixture.student(),
                fixture.course(),
                2025,
                AcademicTerm.SECOND
            )).isTrue();
    }

    @Test
    void 학생별_성적은_과목과_학과를_함께_조회한다() {
        GradeFixture first = saveFixture("first@inha.edu", "CSE101");
        GradeFixture second = saveFixture("second@inha.edu", "FIN101");
        studentGradeRepository.saveAndFlush(grade(
            first.student(),
            first.course(),
            AcademicTerm.SECOND
        ));
        studentGradeRepository.saveAndFlush(grade(
            second.student(),
            second.course(),
            AcademicTerm.SECOND
        ));
        entityManager.clear();

        Student firstStudent = studentRepository.findById(first.student().getId()).orElseThrow();
        List<StudentGrade> found = studentGradeRepository.findAllByStudent(firstStudent);

        assertThat(found)
            .extracting(item -> item.getStudent().getAuthUserId())
            .containsOnly(first.student().getAuthUserId());
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(
            found.getFirst().getCourse()
        )).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(
            found.getFirst().getCourse().getDepartment()
        )).isTrue();
    }

    @Test
    void 같은_과목은_다른_학기나_다른_학생에게_저장할_수_있다() {
        Department department = departmentRepository.saveAndFlush(
            new Department("소프트웨어융합공학과")
        );
        Course course = courseRepository.saveAndFlush(course(department, "CSE101"));
        Student first = studentRepository.saveAndFlush(student("first@inha.edu"));
        Student second = studentRepository.saveAndFlush(student("second@inha.edu"));

        studentGradeRepository.saveAndFlush(grade(first, course, AcademicTerm.FIRST));
        studentGradeRepository.saveAndFlush(grade(first, course, AcademicTerm.SECOND));
        studentGradeRepository.saveAndFlush(grade(second, course, AcademicTerm.SECOND));

        assertThat(studentGradeRepository.count()).isEqualTo(3);
    }

    @Test
    void 같은_학생의_같은_과목과_연도와_학기는_중복할_수_없다() {
        GradeFixture fixture = saveFixture("duplicate@inha.edu", "CSE101");
        studentGradeRepository.saveAndFlush(grade(
            fixture.student(),
            fixture.course(),
            AcademicTerm.SECOND
        ));

        assertThatThrownBy(() -> studentGradeRepository.saveAndFlush(grade(
            fixture.student(),
            fixture.course(),
            AcademicTerm.SECOND
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 비활성_과목은_외부식별자로_조회하지_않는다() {
        GradeFixture fixture = saveFixture("inactive@inha.edu", "CSE101");
        jdbcTemplate.update(
            "UPDATE public.course SET is_active = false WHERE id = ?",
            fixture.course().getId()
        );
        entityManager.clear();

        assertThat(courseRepository.findByEntityIdAndActiveTrue(
            fixture.course().getEntityId()
        )).isEmpty();
    }

    @Test
    void 외부식별자와_학생으로_본인_성적만_조회한다() {
        GradeFixture fixture = saveFixture("owner@inha.edu", "CSE101");
        Student otherStudent = studentRepository.saveAndFlush(student("other@inha.edu"));
        StudentGrade saved = studentGradeRepository.saveAndFlush(grade(
            fixture.student(),
            fixture.course(),
            AcademicTerm.SECOND
        ));
        entityManager.clear();

        assertThat(studentGradeRepository.findByEntityIdAndStudent(
            saved.getEntityId(),
            fixture.student()
        )).isPresent();
        assertThat(studentGradeRepository.findByEntityIdAndStudent(
            saved.getEntityId(),
            otherStudent
        )).isEmpty();
    }

    @Test
    void 수정할_성적을_제외하고_같은_수강이력이_있는지_확인한다() {
        GradeFixture fixture = saveFixture("update@inha.edu", "CSE101");
        StudentGrade first = studentGradeRepository.saveAndFlush(grade(
            fixture.student(),
            fixture.course(),
            AcademicTerm.FIRST
        ));
        studentGradeRepository.saveAndFlush(grade(
            fixture.student(),
            fixture.course(),
            AcademicTerm.SECOND
        ));

        assertThat(studentGradeRepository
            .existsByStudentAndCourseAndAcademicYearAndTermAndIdNot(
                fixture.student(),
                fixture.course(),
                2025,
                AcademicTerm.SECOND,
                first.getId()
            )).isTrue();
        assertThat(studentGradeRepository
            .existsByStudentAndCourseAndAcademicYearAndTermAndIdNot(
                fixture.student(),
                fixture.course(),
                2025,
                AcademicTerm.FIRST,
                first.getId()
            )).isFalse();
    }

    @Test
    void 재수강_성적은_대체하는_이전_성적을_저장한다() {
        GradeFixture fixture = saveFixture("retake@inha.edu", "CSE101");
        StudentGrade previous = studentGradeRepository.saveAndFlush(new StudentGrade(
            fixture.student(),
            fixture.course(),
            2024,
            AcademicTerm.SECOND,
            GradeCode.C_PLUS,
            new BigDecimal("3.0"),
            false,
            (StudentGrade) null
        ));
        StudentGrade retake = studentGradeRepository.saveAndFlush(new StudentGrade(
            fixture.student(),
            fixture.course(),
            2025,
            AcademicTerm.FIRST,
            GradeCode.A_ZERO,
            new BigDecimal("3.0"),
            false,
            previous
        ));
        UUID retakeEntityId = retake.getEntityId();
        entityManager.clear();

        StudentGrade found = studentGradeRepository.findAllByStudent(
            fixture.student()
        ).stream()
            .filter(grade -> grade.getEntityId().equals(retakeEntityId))
            .findFirst()
            .orElseThrow();

        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(
            found,
            "replacedGrade"
        )).isTrue();
        assertThat(found.getReplacedGrade().getEntityId())
            .isEqualTo(previous.getEntityId());
    }

    @Test
    void 하나의_성적은_한_번만_재수강_대상으로_연결할_수_있다() {
        GradeFixture fixture = saveFixture("single-retake@inha.edu", "CSE101");
        StudentGrade previous = studentGradeRepository.saveAndFlush(new StudentGrade(
            fixture.student(),
            fixture.course(),
            2024,
            AcademicTerm.SECOND,
            GradeCode.C_PLUS,
            new BigDecimal("3.0"),
            false,
            (StudentGrade) null
        ));
        studentGradeRepository.saveAndFlush(new StudentGrade(
            fixture.student(),
            fixture.course(),
            2025,
            AcademicTerm.FIRST,
            GradeCode.A_ZERO,
            new BigDecimal("3.0"),
            false,
            previous
        ));

        assertThatThrownBy(() -> studentGradeRepository.saveAndFlush(new StudentGrade(
            fixture.student(),
            fixture.course(),
            2025,
            AcademicTerm.SECOND,
            GradeCode.A_PLUS,
            new BigDecimal("3.0"),
            false,
            previous
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidRawGradeCases")
    void 데이터베이스는_유효하지_않은_성적을_거부한다(
        int academicYear,
        String term,
        String gradeCode,
        String gradePoint,
        String credit,
        boolean rpl
    ) {
        GradeFixture fixture = saveFixture(
            UUID.randomUUID() + "@inha.edu",
            "CSE" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );

        assertThatThrownBy(() -> insertRawGrade(
            fixture.student().getId(),
            fixture.course().getId(),
            academicYear,
            term,
            gradeCode,
            gradePoint,
            credit,
            rpl
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    static Stream<Arguments> invalidRawGradeCases() {
        return Stream.of(
            arguments(1999, "SECOND", "A_PLUS", "4.50", "3.0", false),
            arguments(2025, "INVALID", "A_PLUS", "4.50", "3.0", false),
            arguments(2025, "SECOND", "INVALID", "4.50", "3.0", false),
            arguments(2025, "SECOND", "A_PLUS", "4.51", "3.0", false),
            arguments(2025, "SECOND", "A_PLUS", "4.50", "0.0", false),
            arguments(2025, "SECOND", "A_PLUS", "4.50", "3.0", true)
        );
    }

    private GradeFixture saveFixture(String email, String code) {
        Department department = departmentRepository.saveAndFlush(
            new Department("학과-" + code)
        );
        Student student = studentRepository.saveAndFlush(student(email));
        Course course = courseRepository.saveAndFlush(course(department, code));
        return new GradeFixture(student, course);
    }

    private Student student(String email) {
        return new Student(
            UUID.randomUUID(),
            email,
            2024,
            EnrollmentStatus.ENROLLED,
            "인하"
        );
    }

    private Course course(Department department, String code) {
        return new Course(
            department,
            code,
            "테스트 과목",
            new BigDecimal("3.0"),
            CourseCategory.MAJOR
        );
    }

    private StudentGrade grade(Student student, Course course, AcademicTerm term) {
        return new StudentGrade(
            student,
            course,
            2025,
            term,
            GradeCode.A_PLUS,
            new BigDecimal("3.0"),
            false,
            (StudentGrade) null
        );
    }

    private void insertRawGrade(
        Long studentId,
        Long courseId,
        int academicYear,
        String term,
        String gradeCode,
        String gradePoint,
        String credit,
        boolean rpl
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO public.student_grade (
                    student_id,
                    course_id,
                    academic_year,
                    term,
                    grade_code,
                    grade_point,
                    credit,
                    is_rpl
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            studentId,
            courseId,
            academicYear,
            term,
            gradeCode,
            new BigDecimal(gradePoint),
            new BigDecimal(credit),
            rpl
        );
    }

    private record GradeFixture(Student student, Course course) { }
}
