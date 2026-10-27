package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.GradeCourseResponse;
import com.ahni.backend.dto.GradeRegistrationRequest;
import com.ahni.backend.dto.GradeResponse;
import com.ahni.backend.dto.GradeSummaryResponse;
import com.ahni.backend.dto.GradeUpdateRequest;
import com.ahni.backend.dto.GradeSimulationRequest;
import com.ahni.backend.dto.GradeSimulationResponse;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentGrade;
import com.ahni.backend.exception.CourseNotFoundException;
import com.ahni.backend.exception.GradeAlreadyRegisteredException;
import com.ahni.backend.exception.GradeNotFoundException;
import com.ahni.backend.exception.GradeReplacementConflictException;
import com.ahni.backend.exception.InvalidGradeException;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.repository.StudentGradeRepository;
import com.ahni.backend.repository.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GradeService {
    private static final Comparator<StudentGrade> NEWEST_FIRST =
        Comparator.comparingInt(StudentGrade::getAcademicYear).reversed()
            .thenComparing(
                grade -> grade.getTerm().sequence(),
                Comparator.reverseOrder()
            )
            .thenComparing(grade -> grade.getCourse().getCode());

    private final StudentRepository studentRepository;
    private final CurriculumService curriculumService;
    private final StudentGradeRepository gradeRepository;

    public GradeService(
        StudentRepository studentRepository,
        CurriculumService curriculumService,
        StudentGradeRepository gradeRepository
    ) {
        this.studentRepository = studentRepository;
        this.curriculumService = curriculumService;
        this.gradeRepository = gradeRepository;
    }

    @Transactional
    public GradeResponse register(
        UUID authUserId,
        GradeRegistrationRequest request
    ) {
        Student student = findStudent(authUserId);
        Course course = curriculumService.requireOfferedCourse(request.academicYear(), request.term(), request.courseEntityId());

        if (gradeRepository.existsByStudentAndCourseAndAcademicYearAndTerm(
            student,
            course,
            request.academicYear(),
            request.term()
        )) {
            throw new GradeAlreadyRegisteredException();
        }

        StudentGrade replacedGrade = findOptionalGrade(
            request.replacedGradeEntityId(),
            student
        );
        ensureReplacementAvailable(replacedGrade);

        StudentGrade grade;
        try {
            grade = new StudentGrade(
                student,
                course,
                request.academicYear(),
                request.term(),
                request.gradeCode(),
                request.credit(),
                request.rpl(),
                replacedGrade
            );
        } catch (IllegalArgumentException exception) {
            throw new InvalidGradeException(exception.getMessage());
        }

        try {
            return toResponse(gradeRepository.saveAndFlush(grade));
        } catch (DataIntegrityViolationException exception) {
            throw new GradeAlreadyRegisteredException();
        }
    }

    public List<GradeResponse> getGrades(UUID authUserId) {
        Student student = findStudent(authUserId);

        return gradeRepository.findAllByStudent(student).stream()
            .sorted(NEWEST_FIRST)
            .map(GradeService::toResponse)
            .toList();
    }

    public GradeSummaryResponse getSummary(UUID authUserId) {
        Student student = findStudent(authUserId);
        return GradeSummaryCalculator.calculate(
            gradeRepository.findAllByStudent(student)
        );
    }

    public GradeSimulationResponse simulate(UUID authUserId, GradeSimulationRequest request) {
        Student student = findStudent(authUserId);
        List<StudentGrade> grades = gradeRepository.findAllByStudent(student);
        return new GradeSimulationResponse(
            GradeSummaryCalculator.calculate(grades),
            GradeSummaryCalculator.project(grades, request.expectedGrades())
        );
    }

    @Transactional
    public GradeResponse update(
        UUID authUserId,
        UUID gradeEntityId,
        GradeUpdateRequest request
    ) {
        Student student = findStudent(authUserId);
        StudentGrade grade = findGrade(gradeEntityId, student);

        if (grade.getAcademicYear() != request.academicYear() || grade.getTerm() != request.term()) {
            curriculumService.requireOfferedCourse(request.academicYear(), request.term(), grade.getCourse().getEntityId());
        }

        if (gradeRepository.existsByStudentAndCourseAndAcademicYearAndTermAndIdNot(
            student,
            grade.getCourse(),
            request.academicYear(),
            request.term(),
            grade.getId()
        )) {
            throw new GradeAlreadyRegisteredException();
        }

        ensureValidAsExistingReplacementTarget(grade, request);

        StudentGrade replacedGrade = findOptionalGrade(
            request.replacedGradeEntityId(),
            student
        );
        ensureReplacementAvailable(replacedGrade, grade.getEntityId());

        try {
            grade.update(
                request.academicYear(),
                request.term(),
                request.gradeCode(),
                request.credit(),
                request.rpl(),
                replacedGrade
            );
        } catch (IllegalArgumentException exception) {
            throw new InvalidGradeException(exception.getMessage());
        }

        try {
            return toResponse(gradeRepository.saveAndFlush(grade));
        } catch (DataIntegrityViolationException exception) {
            throw new GradeAlreadyRegisteredException();
        }
    }

    @Transactional
    public void delete(UUID authUserId, UUID gradeEntityId) {
        Student student = findStudent(authUserId);
        StudentGrade grade = findGrade(gradeEntityId, student);
        if (gradeRepository.existsByReplacedGrade(grade)) {
            throw new GradeReplacementConflictException(
                "재수강으로 대체된 이전 성적은 삭제할 수 없습니다."
            );
        }
        gradeRepository.delete(grade);
    }

    private Student findStudent(UUID authUserId) {
        return studentRepository.findByAuthUserId(authUserId)
            .orElseThrow(StudentNotFoundException::new);
    }

    private StudentGrade findGrade(UUID gradeEntityId, Student student) {
        return gradeRepository.findByEntityIdAndStudent(gradeEntityId, student)
            .orElseThrow(GradeNotFoundException::new);
    }

    private StudentGrade findOptionalGrade(UUID gradeEntityId, Student student) {
        return gradeEntityId == null ? null : findGrade(gradeEntityId, student);
    }

    private void ensureReplacementAvailable(StudentGrade replacedGrade) {
        if (replacedGrade != null
            && gradeRepository.existsByReplacedGrade(replacedGrade)) {
            throw new GradeReplacementConflictException(
                "이미 다른 재수강 성적에 연결된 성적입니다."
            );
        }
    }

    private void ensureReplacementAvailable(
        StudentGrade replacedGrade,
        UUID currentGradeEntityId
    ) {
        if (replacedGrade != null
            && gradeRepository.existsByReplacedGradeAndEntityIdNot(
                replacedGrade,
                currentGradeEntityId
            )) {
            throw new GradeReplacementConflictException(
                "이미 다른 재수강 성적에 연결된 성적입니다."
            );
        }
    }

    private void ensureValidAsExistingReplacementTarget(
        StudentGrade grade,
        GradeUpdateRequest request
    ) {
        StudentGrade successor = gradeRepository.findByReplacedGrade(grade)
            .orElse(null);
        if (successor == null) {
            return;
        }
        if (request.rpl()) {
            throw new InvalidGradeException(
                "RPL 성적은 재수강 관계에 포함할 수 없습니다."
            );
        }
        if (!isEarlierPeriod(
            request.academicYear(),
            request.term(),
            successor.getAcademicYear(),
            successor.getTerm()
        )) {
            throw new InvalidGradeException(
                "재수강으로 대체된 성적은 후속 성적보다 이전 학기여야 합니다."
            );
        }
    }

    private static boolean isEarlierPeriod(
        int candidateYear,
        AcademicTerm candidateTerm,
        int academicYear,
        AcademicTerm term
    ) {
        return candidateYear < academicYear
            || candidateYear == academicYear
            && candidateTerm.sequence() < term.sequence();
    }

    private static GradeResponse toResponse(StudentGrade grade) {
        Course course = grade.getCourse();
        Department department = course.getDepartment();
        DepartmentResponse departmentResponse = department == null
            ? null
            : new DepartmentResponse(department.getEntityId(), department.getName());
        GradeCourseResponse courseResponse = new GradeCourseResponse(
            course.getEntityId(),
            course.getCode(),
            course.getName(),
            course.getCategory(),
            departmentResponse
        );

        return new GradeResponse(
            grade.getEntityId(),
            courseResponse,
            grade.getAcademicYear(),
            grade.getTerm(),
            grade.getGradeCode(),
            grade.getGradePoint(),
            grade.getCredit(),
            grade.isRpl(),
            grade.getReplacedGrade() == null
                ? null
                : grade.getReplacedGrade().getEntityId(),
            grade.getCreatedAt(),
            grade.getUpdatedAt()
        );
    }
}
