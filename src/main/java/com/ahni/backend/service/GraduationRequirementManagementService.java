package com.ahni.backend.service;

import com.ahni.backend.dto.CourseResponse;
import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.GraduationRequirementCreateRequest;
import com.ahni.backend.dto.GraduationRequirementResponse;
import com.ahni.backend.dto.GraduationRequirementUpdateRequest;
import com.ahni.backend.dto.RequiredCourseAssignmentRequest;
import com.ahni.backend.dto.RequiredCourseResponse;
import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.domain.RequiredCourseCategory;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.GraduationRequirement;
import com.ahni.backend.entity.MajorType;
import com.ahni.backend.entity.RequiredCourse;
import com.ahni.backend.exception.CourseNotFoundException;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.exception.DepartmentNotFoundException;
import com.ahni.backend.exception.GraduationRequirementAlreadyExistsException;
import com.ahni.backend.exception.GraduationRequirementNotFoundException;
import com.ahni.backend.exception.InvalidRequiredCourseAssignmentException;
import com.ahni.backend.repository.CourseRepository;
import com.ahni.backend.repository.AdminRepository;
import com.ahni.backend.repository.DepartmentRepository;
import com.ahni.backend.repository.GraduationRequirementRepository;
import com.ahni.backend.repository.RequiredCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GraduationRequirementManagementService {
    private static final Comparator<RequiredCourse> COURSE_CODE_ORDER =
        Comparator.comparing(requiredCourse -> requiredCourse.getCourse().getCode());

    private final AdminRepository adminRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final GraduationRequirementRepository graduationRequirementRepository;
    private final RequiredCourseRepository requiredCourseRepository;

    public GraduationRequirementManagementService(
        AdminRepository adminRepository,
        DepartmentRepository departmentRepository,
        CourseRepository courseRepository,
        GraduationRequirementRepository graduationRequirementRepository,
        RequiredCourseRepository requiredCourseRepository
    ) {
        this.adminRepository = adminRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.graduationRequirementRepository = graduationRequirementRepository;
        this.requiredCourseRepository = requiredCourseRepository;
    }

    public List<GraduationRequirementResponse> findAll(
        UUID authUserId,
        UUID departmentEntityId,
        Integer admissionYear,
        String majorType
    ) {
        ensureAdmin(authUserId);
        MajorType parsedMajorType = majorType == null
            ? null
            : MajorType.valueOf(majorType);
        List<GraduationRequirement> requirements = graduationRequirementRepository
            .findAllForAdmin(departmentEntityId, admissionYear, parsedMajorType);
        if (requirements.isEmpty()) {
            return List.of();
        }
        Map<GraduationRequirement, List<RequiredCourse>> requiredCoursesByRequirement =
            requiredCourseRepository
                .findAllActiveByGraduationRequirementIn(requirements)
                .stream()
                .collect(Collectors.groupingBy(
                    RequiredCourse::getGraduationRequirement
                ));

        return requirements.stream()
            .map(requirement -> toResponse(
                requirement,
                requiredCoursesByRequirement.getOrDefault(requirement, List.of())
            ))
            .toList();
    }

    @Transactional
    public GraduationRequirementResponse create(
        UUID authUserId,
        GraduationRequirementCreateRequest request
    ) {
        ensureAdmin(authUserId);
        Department department = departmentRepository
            .findByEntityIdAndDeletedAtIsNull(request.departmentEntityId())
            .orElseThrow(DepartmentNotFoundException::new);
        MajorType majorType = MajorType.valueOf(request.majorType());
        if (graduationRequirementRepository
            .findByDepartmentAndAdmissionYearAndMajorType(
                department,
                request.admissionYear(),
                majorType
            ).isPresent()) {
            throw new GraduationRequirementAlreadyExistsException();
        }

        List<Course> courses = findCourses(request.requiredCourses());
        GraduationRequirement requirement = graduationRequirementRepository.save(
            new GraduationRequirement(
                department,
                request.admissionYear(),
                majorType,
                request.minTotalCredit(),
                request.minDepartmentCredit(),
                request.minGeneralCredit(),
                request.sourceTitle(),
                request.sourceUrl()
            )
        );
        if (request.minFoundationCredit() != null) requirement.setMinFoundationCredit(request.minFoundationCredit());
        List<RequiredCourse> requiredCourses = saveAssignments(
            requirement,
            request.requiredCourses(),
            courses
        );
        return toResponse(requirement, requiredCourses);
    }

    @Transactional
    public GraduationRequirementResponse update(
        UUID authUserId,
        UUID requirementEntityId,
        GraduationRequirementUpdateRequest request
    ) {
        ensureAdmin(authUserId);
        GraduationRequirement requirement = graduationRequirementRepository
            .findByEntityId(requirementEntityId)
            .orElseThrow(GraduationRequirementNotFoundException::new);
        List<RequiredCourse> previousAssignments = requiredCourseRepository
            .findAllActiveByGraduationRequirement(requirement);
        List<Course> courses = findCourses(
            request.requiredCourses(),
            previousAssignments.stream().map(RequiredCourse::getCourse).toList()
        );
        requirement.update(
            request.minTotalCredit(),
            request.minDepartmentCredit(),
            request.minGeneralCredit(),
            request.sourceTitle(),
            request.sourceUrl()
        );
        previousAssignments.forEach(RequiredCourse::softDelete);
        requiredCourseRepository.saveAllAndFlush(previousAssignments);

        if (request.minFoundationCredit() != null) requirement.setMinFoundationCredit(request.minFoundationCredit());
        List<RequiredCourse> requiredCourses = saveAssignments(
            requirement,
            request.requiredCourses(),
            courses
        );
        return toResponse(requirement, requiredCourses);
    }

    private void ensureAdmin(UUID authUserId) {
        if (!adminRepository.existsByAuthUserIdAndDeletedAtIsNull(authUserId)) {
            throw new AdminAccessDeniedException();
        }
    }

    private List<Course> findCourses(
        List<RequiredCourseAssignmentRequest> assignments
    ) {
        return findCourses(assignments, List.of());
    }

    private List<Course> findCourses(
        List<RequiredCourseAssignmentRequest> assignments,
        List<Course> previousCourses
    ) {
        List<UUID> courseEntityIds = assignments.stream()
            .map(RequiredCourseAssignmentRequest::courseEntityId)
            .toList();
        if (new HashSet<>(courseEntityIds).size() != courseEntityIds.size()) {
            throw new InvalidRequiredCourseAssignmentException(
                "같은 과목을 필수과목으로 중복 지정할 수 없습니다."
            );
        }
        List<Course> courses = courseRepository
            .findAllByEntityIdInAndActiveTrue(courseEntityIds);
        Map<UUID, Course> availableCourses = new LinkedHashMap<>();
        courses.forEach(course -> availableCourses.put(course.getEntityId(), course));
        previousCourses.stream()
            .filter(course -> courseEntityIds.contains(course.getEntityId()))
            .forEach(course -> availableCourses.putIfAbsent(course.getEntityId(), course));
        if (availableCourses.size() != courseEntityIds.size()) {
            throw new CourseNotFoundException();
        }
        return List.copyOf(availableCourses.values());
    }

    private List<RequiredCourse> saveAssignments(
        GraduationRequirement requirement,
        List<RequiredCourseAssignmentRequest> assignments,
        List<Course> courses
    ) {
        Map<UUID, Course> coursesByEntityId = new LinkedHashMap<>();
        courses.forEach(course -> coursesByEntityId.put(course.getEntityId(), course));
        List<RequiredCourse> requiredCourses = assignments.stream()
            .map(assignment -> requiredCourse(
                requirement,
                coursesByEntityId.get(assignment.courseEntityId()),
                assignment.category()
            ))
            .toList();
        requiredCourseRepository.saveAll(requiredCourses);
        return requiredCourses.stream().sorted(COURSE_CODE_ORDER).toList();
    }

    private RequiredCourse requiredCourse(
        GraduationRequirement requirement,
        Course course,
        RequiredCourseCategory category
    ) {
        CourseCategory expectedCategory = category == RequiredCourseCategory.GENERAL_REQUIRED
            ? CourseCategory.GENERAL_EDUCATION
            : CourseCategory.MAJOR;
        if (course.getCategory() != expectedCategory) {
            throw new InvalidRequiredCourseAssignmentException(
                "필수과목 분류와 과목 분류가 일치하지 않습니다."
            );
        }
        return new RequiredCourse(requirement, course, category);
    }

    private static GraduationRequirementResponse toResponse(
        GraduationRequirement requirement,
        List<RequiredCourse> requiredCourses
    ) {
        Department department = requirement.getDepartment();
        return new GraduationRequirementResponse(
            requirement.getEntityId(),
            requirement.getAdmissionYear(),
            requirement.getMajorType().name(),
            new DepartmentResponse(department.getEntityId(), department.getName()),
            requirement.getMinTotalCredit(),
            requirement.getMinDepartmentCredit(),
            requirement.getMinGeneralCredit(),
            requirement.getSourceTitle(),
            requirement.getSourceUrl(),
            requiredCourses.stream()
                .sorted(COURSE_CODE_ORDER)
                .map(GraduationRequirementManagementService::toRequiredCourseResponse)
                .toList(),
            requirement.getMinFoundationCredit(), requirement.isEvaluationComplete()
        );
    }

    private static RequiredCourseResponse toRequiredCourseResponse(
        RequiredCourse requiredCourse
    ) {
        Course course = requiredCourse.getCourse();
        Department department = course.getDepartment();
        DepartmentResponse departmentResponse = department == null
            ? null
            : new DepartmentResponse(department.getEntityId(), department.getName());
        return new RequiredCourseResponse(
            requiredCourse.getEntityId(),
            requiredCourse.getCategory(),
            new CourseResponse(
                course.getEntityId(),
                course.getCode(),
                course.getName(),
                course.getCredit(),
                course.getCategory(),
                departmentResponse
            )
        );
    }
}
