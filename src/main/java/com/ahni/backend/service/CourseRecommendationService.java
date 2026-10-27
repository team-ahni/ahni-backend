package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.dto.CourseRecommendationResponse;
import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.LectureResponse;
import com.ahni.backend.dto.MajorCourseRecommendationResponse;
import com.ahni.backend.dto.RecommendedCourseResponse;
import com.ahni.backend.entity.CurriculumCourse;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentGrade;
import com.ahni.backend.entity.StudentMajor;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.repository.CurriculumCourseRepository;
import com.ahni.backend.repository.GraduationRequirementRepository;
import com.ahni.backend.repository.LectureRepository;
import com.ahni.backend.repository.RequiredCourseRepository;
import com.ahni.backend.repository.StudentGradeRepository;
import com.ahni.backend.repository.StudentMajorRepository;
import com.ahni.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CourseRecommendationService {
    private final StudentRepository studentRepository;
    private final StudentMajorRepository studentMajorRepository;
    private final StudentGradeRepository gradeRepository;
    private final CurriculumCourseRepository curriculumCourseRepository;
    private final GraduationRequirementRepository graduationRequirementRepository;
    private final RequiredCourseRepository requiredCourseRepository;
    private final LectureRepository lectureRepository;

    public CourseRecommendationService(
        StudentRepository studentRepository,
        StudentMajorRepository studentMajorRepository,
        StudentGradeRepository gradeRepository,
        CurriculumCourseRepository curriculumCourseRepository,
        GraduationRequirementRepository graduationRequirementRepository,
        RequiredCourseRepository requiredCourseRepository,
        LectureRepository lectureRepository
    ) {
        this.studentRepository = studentRepository;
        this.studentMajorRepository = studentMajorRepository;
        this.gradeRepository = gradeRepository;
        this.curriculumCourseRepository = curriculumCourseRepository;
        this.graduationRequirementRepository = graduationRequirementRepository;
        this.requiredCourseRepository = requiredCourseRepository;
        this.lectureRepository = lectureRepository;
    }

    public CourseRecommendationResponse recommend(UUID authUserId, int academicYear, AcademicTerm term) {
        // 권장 학기는 정규학기 기준이라 계절학기 추천은 지원하지 않는다.
        if (academicYear < 2000 || academicYear > 9999
            || (term != AcademicTerm.FIRST && term != AcademicTerm.SECOND)) {
            throw new IllegalArgumentException("정규학기 연도와 학기를 입력해 주세요.");
        }
        Student student = studentRepository.findByAuthUserId(authUserId)
            .orElseThrow(StudentNotFoundException::new);
        List<StudentGrade> grades = gradeRepository.findAllByStudent(student);
        List<StudentGrade> effectiveAttempts = GradeSummaryCalculator.effectiveAttempts(grades);
        int semesterNumber = CourseRecommendationCalculator.semesterNumber(grades, academicYear, term);

        Map<UUID, List<LectureResponse>> lectures = lectureRepository
            .findAllByAcademicYearAndTermOrderByCourseCodeAscSectionAsc(academicYear, term).stream()
            .map(LectureService::response)
            .collect(Collectors.groupingBy(LectureResponse::courseEntityId));
        Map<UUID, List<LectureResponse>> lecturesByCourse = lectures.isEmpty() ? null : lectures;

        List<StudentMajor> majors = studentMajorRepository.findAllByStudentAndDeletedAtIsNull(student).stream()
            .sorted(Comparator.comparing(StudentMajor::getMajorType))
            .toList();
        List<CurriculumCourse> links = majors.isEmpty() ? List.of()
            : curriculumCourseRepository.findPublishedRecognitionLinks(
                majors.stream().map(major -> major.getDepartment().getEntityId()).distinct().toList(),
                List.of(academicYear));

        Set<UUID> alreadyRecommended = new HashSet<>();
        List<MajorCourseRecommendationResponse> responses = majors.stream().map(major -> {
            List<CurriculumCourse> departmentLinks = links.stream()
                .filter(link -> link.getCurriculum().getDepartment().getEntityId()
                    .equals(major.getDepartment().getEntityId()))
                .toList();
            List<CurriculumCourse> majorLinks = departmentLinks.stream()
                .filter(link -> !alreadyRecommended.contains(link.getCourse().getEntityId()))
                .toList();
            List<RecommendedCourseResponse> courses = CourseRecommendationCalculator.recommend(
                majorLinks, graduationRequiredCourseIds(student, major), effectiveAttempts,
                semesterNumber, lecturesByCourse);
            courses.forEach(course -> alreadyRecommended.add(course.course().entityId()));
            return new MajorCourseRecommendationResponse(
                major.getMajorType().name(),
                new DepartmentResponse(major.getDepartment().getEntityId(), major.getDepartment().getName()),
                !departmentLinks.isEmpty(),
                courses
            );
        }).toList();

        return new CourseRecommendationResponse(academicYear, term, semesterNumber, lecturesByCourse != null, responses);
    }

    private Set<UUID> graduationRequiredCourseIds(Student student, StudentMajor major) {
        return graduationRequirementRepository
            .findByDepartmentAndAdmissionYearAndMajorType(major.getDepartment(), student.getAdmissionYear(), major.getMajorType())
            .map(requirement -> requiredCourseRepository.findAllActiveByGraduationRequirement(requirement).stream()
                .map(requiredCourse -> requiredCourse.getCourse().getEntityId())
                .collect(Collectors.toSet()))
            .orElse(Set.of());
    }
}
