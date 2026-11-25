package com.ahni.backend.service;

import com.ahni.backend.dto.CourseResponse;
import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.GraduationRequirementResponse;
import com.ahni.backend.dto.RequiredCourseResponse;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.GraduationRequirement;
import com.ahni.backend.entity.RequiredCourse;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentMajor;
import com.ahni.backend.exception.GraduationRequirementNotFoundException;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.repository.GraduationRequirementRepository;
import com.ahni.backend.repository.RequiredCourseRepository;
import com.ahni.backend.repository.StudentMajorRepository;
import com.ahni.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GraduationRequirementService {
    private static final Comparator<StudentMajor> MAJOR_TYPE_ORDER =
        Comparator.comparing(StudentMajor::getMajorType);

    private final StudentRepository studentRepository;
    private final StudentMajorRepository studentMajorRepository;
    private final GraduationRequirementRepository graduationRequirementRepository;
    private final RequiredCourseRepository requiredCourseRepository;

    public GraduationRequirementService(
        StudentRepository studentRepository,
        StudentMajorRepository studentMajorRepository,
        GraduationRequirementRepository graduationRequirementRepository,
        RequiredCourseRepository requiredCourseRepository
    ) {
        this.studentRepository = studentRepository;
        this.studentMajorRepository = studentMajorRepository;
        this.graduationRequirementRepository = graduationRequirementRepository;
        this.requiredCourseRepository = requiredCourseRepository;
    }

    public List<GraduationRequirementResponse> getRequirements(UUID authUserId) {
        Student student = studentRepository.findByAuthUserId(authUserId)
            .orElseThrow(StudentNotFoundException::new);

        return studentMajorRepository.findAllByStudentAndDeletedAtIsNull(student).stream()
            .sorted(MAJOR_TYPE_ORDER)
            .map(major -> getRequirement(student, major))
            .toList();
    }

    private GraduationRequirementResponse getRequirement(
        Student student,
        StudentMajor major
    ) {
        GraduationRequirement requirement = graduationRequirementRepository
            .findByDepartmentAndAdmissionYearAndMajorType(
                major.getDepartment(),
                student.getAdmissionYear(),
                major.getMajorType()
            )
            .orElseThrow(GraduationRequirementNotFoundException::new);
        List<RequiredCourseResponse> requiredCourses = requiredCourseRepository
            .findAllActiveByGraduationRequirement(requirement).stream()
            .map(GraduationRequirementService::toRequiredCourseResponse)
            .toList();

        return new GraduationRequirementResponse(
            requirement.getEntityId(),
            requirement.getAdmissionYear(),
            requirement.getMajorType().name(),
            toDepartmentResponse(requirement.getDepartment()),
            requirement.getMinTotalCredit(),
            requirement.getMinDepartmentCredit(),
            requirement.getMinGeneralCredit(),
            requirement.getSourceTitle(),
            requirement.getSourceUrl(),
            requiredCourses, requirement.getMinFoundationCredit(), requirement.isEvaluationComplete()
        );
    }

    private static RequiredCourseResponse toRequiredCourseResponse(
        RequiredCourse requiredCourse
    ) {
        return new RequiredCourseResponse(
            requiredCourse.getEntityId(),
            requiredCourse.getCategory(),
            toCourseResponse(requiredCourse.getCourse())
        );
    }

    private static CourseResponse toCourseResponse(Course course) {
        Department department = course.getDepartment();
        return new CourseResponse(
            course.getEntityId(),
            course.getCode(),
            course.getName(),
            course.getCredit(),
            course.getCategory(),
            department == null ? null : toDepartmentResponse(department)
        );
    }

    private static DepartmentResponse toDepartmentResponse(Department department) {
        return new DepartmentResponse(department.getEntityId(), department.getName());
    }
}
