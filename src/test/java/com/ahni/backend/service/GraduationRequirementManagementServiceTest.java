package com.ahni.backend.service;

import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.domain.RequiredCourseCategory;
import com.ahni.backend.dto.GraduationRequirementCreateRequest;
import com.ahni.backend.dto.GraduationRequirementResponse;
import com.ahni.backend.dto.GraduationRequirementUpdateRequest;
import com.ahni.backend.dto.RequiredCourseAssignmentRequest;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.GraduationRequirement;
import com.ahni.backend.entity.MajorType;
import com.ahni.backend.entity.RequiredCourse;
import com.ahni.backend.exception.GraduationRequirementAlreadyExistsException;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.exception.CourseNotFoundException;
import com.ahni.backend.exception.InvalidRequiredCourseAssignmentException;
import com.ahni.backend.repository.AdminRepository;
import com.ahni.backend.repository.CourseRepository;
import com.ahni.backend.repository.DepartmentRepository;
import com.ahni.backend.repository.GraduationRequirementRepository;
import com.ahni.backend.repository.RequiredCourseRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraduationRequirementManagementServiceTest {
    private static final UUID ADMIN_AUTH_USER_ID = UUID.randomUUID();

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private GraduationRequirementRepository graduationRequirementRepository;

    @Mock
    private RequiredCourseRepository requiredCourseRepository;

    @InjectMocks
    private GraduationRequirementManagementService service;

    @Test
    void 졸업요건과_필수과목을_함께_등록한다() {
        allowAdmin();
        Department department = new Department("소프트웨어융합공학과");
        Course majorCourse = course(
            department,
            "CSE101",
            CourseCategory.MAJOR
        );
        Course generalCourse = course(
            null,
            "GED101",
            CourseCategory.GENERAL_EDUCATION
        );
        GraduationRequirementCreateRequest request = createRequest(
            department.getEntityId(),
            List.of(
                assignment(
                    majorCourse.getEntityId(),
                    RequiredCourseCategory.MAJOR_FOUNDATION
                ),
                assignment(
                    generalCourse.getEntityId(),
                    RequiredCourseCategory.GENERAL_REQUIRED
                )
            )
        );
        when(departmentRepository.findByEntityIdAndDeletedAtIsNull(
            department.getEntityId()
        )).thenReturn(Optional.of(department));
        when(graduationRequirementRepository
            .findByDepartmentAndAdmissionYearAndMajorType(
                department,
                2024,
                MajorType.PRIMARY
            )).thenReturn(Optional.empty());
        when(courseRepository.findAllByEntityIdInAndActiveTrue(
            List.of(majorCourse.getEntityId(), generalCourse.getEntityId())
        )).thenReturn(List.of(majorCourse, generalCourse));
        when(graduationRequirementRepository.save(any(GraduationRequirement.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(requiredCourseRepository.saveAll(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        GraduationRequirementResponse response = service.create(
            ADMIN_AUTH_USER_ID,
            request
        );

        assertThat(response.department().entityId()).isEqualTo(department.getEntityId());
        assertThat(response.admissionYear()).isEqualTo(2024);
        assertThat(response.majorType()).isEqualTo("PRIMARY");
        assertThat(response.sourceTitle()).isEqualTo("2024학년도 졸업요건");
        assertThat(response.sourceUrl())
            .isEqualTo("https://example.edu/requirements/2024");
        assertThat(response.requiredCourses())
            .extracting(requiredCourse -> requiredCourse.course().code())
            .containsExactly("CSE101", "GED101");
    }

    @Test
    void 관리자용_졸업요건_목록은_필수과목을_포함한다() {
        allowAdmin();
        Department department = new Department("소프트웨어융합공학과");
        GraduationRequirement requirement = requirement(department);
        Course course = course(department, "CSE101", CourseCategory.MAJOR);
        RequiredCourse requiredCourse = new RequiredCourse(
            requirement,
            course,
            RequiredCourseCategory.MAJOR_FOUNDATION
        );
        when(graduationRequirementRepository.findAllForAdmin(
            department.getEntityId(),
            2024,
            MajorType.PRIMARY
        )).thenReturn(List.of(requirement));
        when(requiredCourseRepository.findAllActiveByGraduationRequirementIn(
            List.of(requirement)
        )).thenReturn(List.of(requiredCourse));

        List<GraduationRequirementResponse> responses = service.findAll(
            ADMIN_AUTH_USER_ID,
            department.getEntityId(),
            2024,
            "PRIMARY"
        );

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.entityId()).isEqualTo(requirement.getEntityId());
            assertThat(response.requiredCourses())
                .extracting(item -> item.course().code())
                .containsExactly("CSE101");
        });
    }

    @Test
    void 활성_관리자가_아니면_졸업요건_목록을_조회할_수_없다() {
        when(adminRepository.existsByAuthUserIdAndDeletedAtIsNull(
            ADMIN_AUTH_USER_ID
        )).thenReturn(false);

        assertThatThrownBy(() -> service.findAll(
            ADMIN_AUTH_USER_ID,
            null,
            null,
            null
        )).isInstanceOf(AdminAccessDeniedException.class);
    }

    @Test
    void 같은_학과_입학연도_전공유형의_졸업요건은_중복_등록할_수_없다() {
        allowAdmin();
        Department department = new Department("소프트웨어융합공학과");
        GraduationRequirement existing = requirement(department);
        GraduationRequirementCreateRequest request = createRequest(
            department.getEntityId(),
            List.of()
        );
        when(departmentRepository.findByEntityIdAndDeletedAtIsNull(
            department.getEntityId()
        )).thenReturn(Optional.of(department));
        when(graduationRequirementRepository
            .findByDepartmentAndAdmissionYearAndMajorType(
                department,
                2024,
                MajorType.PRIMARY
            )).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(ADMIN_AUTH_USER_ID, request))
            .isInstanceOf(GraduationRequirementAlreadyExistsException.class);
    }

    @Test
    void 같은_과목을_필수과목으로_중복_지정할_수_없다() {
        allowAdmin();
        Department department = new Department("소프트웨어융합공학과");
        Course course = course(department, "CSE101", CourseCategory.MAJOR);
        GraduationRequirementCreateRequest request = createRequest(
            department.getEntityId(),
            List.of(
                assignment(
                    course.getEntityId(),
                    RequiredCourseCategory.MAJOR_FOUNDATION
                ),
                assignment(
                    course.getEntityId(),
                    RequiredCourseCategory.MAJOR_REQUIRED
                )
            )
        );
        when(departmentRepository.findByEntityIdAndDeletedAtIsNull(
            department.getEntityId()
        )).thenReturn(Optional.of(department));
        when(graduationRequirementRepository
            .findByDepartmentAndAdmissionYearAndMajorType(
                department,
                2024,
                MajorType.PRIMARY
            )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(ADMIN_AUTH_USER_ID, request))
            .isInstanceOf(InvalidRequiredCourseAssignmentException.class);
    }

    @Test
    void 교양필수에는_교양과목만_지정할_수_있다() {
        allowAdmin();
        Department department = new Department("소프트웨어융합공학과");
        Course majorCourse = course(department, "CSE101", CourseCategory.MAJOR);
        GraduationRequirementCreateRequest request = createRequest(
            department.getEntityId(),
            List.of(assignment(
                majorCourse.getEntityId(),
                RequiredCourseCategory.GENERAL_REQUIRED
            ))
        );
        when(departmentRepository.findByEntityIdAndDeletedAtIsNull(
            department.getEntityId()
        )).thenReturn(Optional.of(department));
        when(graduationRequirementRepository
            .findByDepartmentAndAdmissionYearAndMajorType(
                department,
                2024,
                MajorType.PRIMARY
            )).thenReturn(Optional.empty());
        when(courseRepository.findAllByEntityIdInAndActiveTrue(
            List.of(majorCourse.getEntityId())
        )).thenReturn(List.of(majorCourse));

        assertThatThrownBy(() -> service.create(ADMIN_AUTH_USER_ID, request))
            .isInstanceOf(InvalidRequiredCourseAssignmentException.class);
    }

    @Test
    void 졸업요건_수정은_학점과_출처와_필수과목_구성을_교체한다() {
        allowAdmin();
        Department department = new Department("소프트웨어융합공학과");
        GraduationRequirement requirement = requirement(department);
        requirement.setMinFoundationCredit(new BigDecimal("15"));
        requirement.markEvaluationIncomplete("{\"pending\":\"conditional_rules\"}");
        Course previousCourse = course(
            department,
            "CSE101",
            CourseCategory.MAJOR
        );
        Course replacementCourse = course(
            department,
            "CSE201",
            CourseCategory.MAJOR
        );
        RequiredCourse previous = new RequiredCourse(
            requirement,
            previousCourse,
            RequiredCourseCategory.MAJOR_FOUNDATION
        );
        GraduationRequirementUpdateRequest request = new GraduationRequirementUpdateRequest(
            new BigDecimal("135.0"),
            new BigDecimal("65.0"),
            new BigDecimal("32.0"),
            "2024학년도 개정 졸업요건",
            null,
            List.of(assignment(
                replacementCourse.getEntityId(),
                RequiredCourseCategory.MAJOR_REQUIRED
            ))
        );
        when(graduationRequirementRepository.findByEntityId(requirement.getEntityId()))
            .thenReturn(Optional.of(requirement));
        when(requiredCourseRepository.findAllActiveByGraduationRequirement(requirement))
            .thenReturn(List.of(previous));
        when(courseRepository.findAllByEntityIdInAndActiveTrue(
            List.of(replacementCourse.getEntityId())
        )).thenReturn(List.of(replacementCourse));
        when(requiredCourseRepository.saveAll(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        GraduationRequirementResponse response = service.update(
            ADMIN_AUTH_USER_ID,
            requirement.getEntityId(),
            request
        );

        assertThat(response.minTotalCredit()).isEqualByComparingTo("135.0");
        assertThat(response.minFoundationCredit()).isEqualByComparingTo("15");
        assertThat(response.evaluationComplete()).isFalse();
        assertThat(response.sourceTitle()).isEqualTo("2024학년도 개정 졸업요건");
        assertThat(response.sourceUrl()).isNull();
        assertThat(previous.isActive()).isFalse();
        assertThat(response.requiredCourses())
            .extracting(requiredCourse -> requiredCourse.course().code())
            .containsExactly("CSE201");
    }

    @Test
    void 활성_관리자가_아니면_졸업요건을_등록할_수_없다() {
        GraduationRequirementCreateRequest request = createRequest(
            UUID.randomUUID(),
            List.of()
        );
        when(adminRepository.existsByAuthUserIdAndDeletedAtIsNull(
            ADMIN_AUTH_USER_ID
        )).thenReturn(false);

        assertThatThrownBy(() -> service.create(ADMIN_AUTH_USER_ID, request))
            .isInstanceOf(AdminAccessDeniedException.class);
    }

    private void allowAdmin() {
        when(adminRepository.existsByAuthUserIdAndDeletedAtIsNull(
            ADMIN_AUTH_USER_ID
        )).thenReturn(true);
    }

    @Test
    void policy_edits_can_retain_previously_assigned_inactive_courses() {
        allowAdmin();
        var department = new Department("소프트웨어융합공학과");
        var requirement = requirement(department);
        var course = course(department, "CSE101", CourseCategory.MAJOR);
        course.deactivate();
        var previous = new RequiredCourse(requirement, course, RequiredCourseCategory.MAJOR_REQUIRED);
        when(graduationRequirementRepository.findByEntityId(requirement.getEntityId())).thenReturn(Optional.of(requirement));
        when(requiredCourseRepository.findAllActiveByGraduationRequirement(requirement)).thenReturn(List.of(previous));
        var input = new GraduationRequirementUpdateRequest(new BigDecimal("130.0"), new BigDecimal("60.0"), new BigDecimal("30.0"), "개정 자료", null, List.of(assignment(course.getEntityId(), RequiredCourseCategory.MAJOR_REQUIRED)));
        var result = service.update(ADMIN_AUTH_USER_ID, requirement.getEntityId(), input);
        assertThat(result.requiredCourses()).hasSize(1);
        assertThat(result.requiredCourses().getFirst().course().entityId()).isEqualTo(course.getEntityId());
    }

    @Test
    void policy_edits_cannot_add_unassigned_inactive_courses() {
        allowAdmin();
        var department = new Department("소프트웨어융합공학과");
        var requirement = requirement(department);
        var course = course(department, "CSE101", CourseCategory.MAJOR);
        course.deactivate();
        when(graduationRequirementRepository.findByEntityId(requirement.getEntityId()))
            .thenReturn(Optional.of(requirement));
        var input = new GraduationRequirementUpdateRequest(
            new BigDecimal("130.0"), new BigDecimal("60.0"), new BigDecimal("30.0"),
            "개정 자료", null,
            List.of(assignment(course.getEntityId(), RequiredCourseCategory.MAJOR_REQUIRED))
        );
        assertThatThrownBy(() -> service.update(ADMIN_AUTH_USER_ID, requirement.getEntityId(), input))
            .isInstanceOf(CourseNotFoundException.class);
    }

    private GraduationRequirementCreateRequest createRequest(
        UUID departmentEntityId,
        List<RequiredCourseAssignmentRequest> requiredCourses
    ) {
        return new GraduationRequirementCreateRequest(
            departmentEntityId,
            2024,
            "PRIMARY",
            new BigDecimal("130.0"),
            new BigDecimal("60.0"),
            new BigDecimal("30.0"),
            "2024학년도 졸업요건",
            "https://example.edu/requirements/2024",
            requiredCourses
        );
    }

    private RequiredCourseAssignmentRequest assignment(
        UUID courseEntityId,
        RequiredCourseCategory category
    ) {
        return new RequiredCourseAssignmentRequest(courseEntityId, category);
    }

    private GraduationRequirement requirement(Department department) {
        return new GraduationRequirement(
            department,
            2024,
            MajorType.PRIMARY,
            new BigDecimal("130.0"),
            new BigDecimal("60.0"),
            new BigDecimal("30.0"),
            "2024학년도 졸업요건",
            "https://example.edu/requirements/2024"
        );
    }

    private Course course(
        Department department,
        String code,
        CourseCategory category
    ) {
        return new Course(
            department,
            code,
            code,
            new BigDecimal("3.0"),
            category
        );
    }
}
