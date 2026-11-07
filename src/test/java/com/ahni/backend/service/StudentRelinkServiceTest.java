package com.ahni.backend.service;

import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.domain.StudentRelinkStatus;
import com.ahni.backend.entity.Admin;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.MajorType;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentMajor;
import com.ahni.backend.entity.StudentRelinkRequest;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.exception.StudentAlreadyRegisteredException;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.exception.StudentRelinkConflictException;
import com.ahni.backend.repository.StudentMajorRepository;
import com.ahni.backend.repository.StudentRelinkRequestRepository;
import com.ahni.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentRelinkServiceTest {
    @Mock
    private StudentRepository students;

    @Mock
    private StudentMajorRepository majors;

    @Mock
    private StudentRelinkRequestRepository requests;

    @Mock
    private AdminAccessService access;

    private StudentRelinkService service;
    private UUID oldAuthUserId;
    private UUID newAuthUserId;
    private UUID adminAuthUserId;
    private Student student;
    private Admin admin;

    @BeforeEach
    void setUp() {
        service = new StudentRelinkService(students, majors, requests, access);
        oldAuthUserId = UUID.randomUUID();
        newAuthUserId = UUID.randomUUID();
        adminAuthUserId = UUID.randomUUID();
        student = new Student(oldAuthUserId, "student@inha.edu", 2024, EnrollmentStatus.ENROLLED, "인하");
        admin = mock(Admin.class);
        lenient().when(majors.findAllByStudentAndDeletedAtIsNull(student)).thenReturn(List.of(
            new StudentMajor(student, new Department("소프트웨어융합공학과"), MajorType.PRIMARY)));
    }

    @Test
    void 이메일이_같은_기존_프로필에_대한_재연결_요청을_만든다() {
        when(students.findByAuthUserId(newAuthUserId)).thenReturn(Optional.empty());
        when(requests.findByNewAuthUserIdAndStatus(newAuthUserId, StudentRelinkStatus.PENDING)).thenReturn(Optional.empty());
        when(students.findByEmailIgnoreCaseAndDeletedAtIsNull("Student@inha.edu")).thenReturn(Optional.of(student));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.request(newAuthUserId, "Student@inha.edu");

        assertThat(result.status()).isEqualTo(StudentRelinkStatus.PENDING);
        assertThat(student.getAuthUserId()).isEqualTo(oldAuthUserId);
    }

    @Test
    void 대기_중인_요청이_있으면_새로_만들지_않는다() {
        StudentRelinkRequest pending = new StudentRelinkRequest(student, newAuthUserId, "student@inha.edu");
        when(students.findByAuthUserId(newAuthUserId)).thenReturn(Optional.empty());
        when(requests.findByNewAuthUserIdAndStatus(newAuthUserId, StudentRelinkStatus.PENDING)).thenReturn(Optional.of(pending));

        var result = service.request(newAuthUserId, "student@inha.edu");

        assertThat(result.entityId()).isEqualTo(pending.getEntityId());
        verify(requests, never()).save(any());
    }

    @Test
    void 이미_프로필이_있거나_기존_프로필이_없으면_요청할_수_없다() {
        when(students.findByAuthUserId(oldAuthUserId)).thenReturn(Optional.of(student));
        assertThatThrownBy(() -> service.request(oldAuthUserId, "student@inha.edu"))
            .isInstanceOf(StudentAlreadyRegisteredException.class);

        when(students.findByAuthUserId(newAuthUserId)).thenReturn(Optional.empty());
        when(requests.findByNewAuthUserIdAndStatus(newAuthUserId, StudentRelinkStatus.PENDING)).thenReturn(Optional.empty());
        when(students.findByEmailIgnoreCaseAndDeletedAtIsNull("other@inha.edu")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request(newAuthUserId, "other@inha.edu"))
            .isInstanceOf(StudentNotFoundException.class);
    }

    @Test
    void 승인하면_프로필을_새_계정에_연결하고_다른_대기_요청은_거절한다() {
        StudentRelinkRequest request = new StudentRelinkRequest(student, newAuthUserId, "student@inha.edu");
        StudentRelinkRequest other = new StudentRelinkRequest(student, UUID.randomUUID(), "student@inha.edu");
        when(access.getAdmin(adminAuthUserId)).thenReturn(admin);
        when(requests.findByEntityId(request.getEntityId())).thenReturn(Optional.of(request));
        when(students.findByAuthUserId(newAuthUserId)).thenReturn(Optional.empty());
        when(requests.findAllByStudentAndStatus(student, StudentRelinkStatus.PENDING)).thenReturn(List.of(request, other));

        var result = service.approve(adminAuthUserId, request.getEntityId());

        assertThat(result.status()).isEqualTo(StudentRelinkStatus.APPROVED);
        assertThat(student.getAuthUserId()).isEqualTo(newAuthUserId);
        assertThat(request.getPreviousAuthUserId()).isEqualTo(oldAuthUserId);
        assertThat(request.getResolvedByAdmin()).isSameAs(admin);
        assertThat(other.getStatus()).isEqualTo(StudentRelinkStatus.REJECTED);
    }

    @Test
    void 처리된_요청이나_프로필이_바뀐_요청은_승인할_수_없다() {
        StudentRelinkRequest rejected = new StudentRelinkRequest(student, newAuthUserId, "student@inha.edu");
        rejected.reject(admin);
        StudentRelinkRequest stale = new StudentRelinkRequest(student, UUID.randomUUID(), "previous@inha.edu");
        when(access.getAdmin(adminAuthUserId)).thenReturn(admin);
        when(requests.findByEntityId(rejected.getEntityId())).thenReturn(Optional.of(rejected));
        when(requests.findByEntityId(stale.getEntityId())).thenReturn(Optional.of(stale));
        when(students.findByAuthUserId(stale.getNewAuthUserId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(adminAuthUserId, rejected.getEntityId()))
            .isInstanceOf(StudentRelinkConflictException.class);
        assertThatThrownBy(() -> service.approve(adminAuthUserId, stale.getEntityId()))
            .isInstanceOf(StudentRelinkConflictException.class);
        assertThat(student.getAuthUserId()).isEqualTo(oldAuthUserId);
    }

    @Test
    void 거절하면_프로필은_그대로_둔다() {
        StudentRelinkRequest request = new StudentRelinkRequest(student, newAuthUserId, "student@inha.edu");
        when(access.getAdmin(adminAuthUserId)).thenReturn(admin);
        when(requests.findByEntityId(request.getEntityId())).thenReturn(Optional.of(request));

        var result = service.reject(adminAuthUserId, request.getEntityId());

        assertThat(result.status()).isEqualTo(StudentRelinkStatus.REJECTED);
        assertThat(student.getAuthUserId()).isEqualTo(oldAuthUserId);
    }

    @Test
    void 관리자가_아니면_요청_목록을_볼_수_없다() {
        when(access.getAdmin(adminAuthUserId)).thenThrow(new AdminAccessDeniedException());

        assertThatThrownBy(() -> service.findAll(adminAuthUserId))
            .isInstanceOf(AdminAccessDeniedException.class);
        verify(requests, never()).findAllByOrderByCreatedAtDesc();
    }
}
