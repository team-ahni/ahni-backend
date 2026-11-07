package com.ahni.backend.service;

import com.ahni.backend.domain.StudentRelinkStatus;
import com.ahni.backend.dto.AdminStudentRelinkRequestResponse;
import com.ahni.backend.dto.StudentRelinkRequestResponse;
import com.ahni.backend.entity.Admin;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentRelinkRequest;
import com.ahni.backend.exception.StudentAlreadyRegisteredException;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.exception.StudentRelinkConflictException;
import com.ahni.backend.exception.StudentRelinkRequestNotFoundException;
import com.ahni.backend.repository.StudentMajorRepository;
import com.ahni.backend.repository.StudentRelinkRequestRepository;
import com.ahni.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Supabase 계정이 다시 만들어져 기존 프로필과 이메일이 충돌한 학생을 관리자 확인 후 새 계정에 연결한다.
 * 학교 이메일은 재발급될 수 있어 이메일만으로 자동 연결하지 않는다(docs/security/README.md).
 */
@Service
@Transactional(readOnly = true)
public class StudentRelinkService {
    private final StudentRepository students;
    private final StudentMajorRepository majors;
    private final StudentRelinkRequestRepository requests;
    private final AdminAccessService access;

    public StudentRelinkService(
        StudentRepository students,
        StudentMajorRepository majors,
        StudentRelinkRequestRepository requests,
        AdminAccessService access
    ) {
        this.students = students;
        this.majors = majors;
        this.requests = requests;
        this.access = access;
    }

    @Transactional
    public StudentRelinkRequestResponse request(UUID authUserId, String email) {
        if (students.findByAuthUserId(authUserId).isPresent()) {
            throw new StudentAlreadyRegisteredException();
        }
        StudentRelinkRequest request = requests
            .findByNewAuthUserIdAndStatus(authUserId, StudentRelinkStatus.PENDING)
            .orElseGet(() -> {
                Student student = email == null ? null
                    : students.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElse(null);
                if (student == null) {
                    throw new StudentNotFoundException();
                }
                return requests.save(new StudentRelinkRequest(student, authUserId, email));
            });
        return new StudentRelinkRequestResponse(request.getEntityId(), request.getStatus(), request.getCreatedAt());
    }

    public List<AdminStudentRelinkRequestResponse> findAll(UUID adminAuthUserId) {
        access.getAdmin(adminAuthUserId);
        return requests.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList();
    }

    @Transactional
    public AdminStudentRelinkRequestResponse approve(UUID adminAuthUserId, UUID requestEntityId) {
        Admin admin = access.getAdmin(adminAuthUserId);
        StudentRelinkRequest request = findPending(requestEntityId);
        Student student = request.getStudent();
        if (students.findByAuthUserId(request.getNewAuthUserId()).isPresent()) {
            throw new StudentRelinkConflictException("새 계정에 이미 다른 학생 프로필이 연결되어 있습니다.");
        }
        if (student.getDeletedAt() != null || !student.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new StudentRelinkConflictException("요청 이후 기존 학생 프로필이 변경되어 연결할 수 없습니다.");
        }
        request.approve(admin);
        // 같은 프로필을 노리는 다른 대기 요청은 남겨 두면 다시 승인될 수 있으므로 함께 거절한다.
        requests.findAllByStudentAndStatus(student, StudentRelinkStatus.PENDING).stream()
            .filter(other -> other != request)
            .forEach(other -> other.reject(admin));
        return response(request);
    }

    @Transactional
    public AdminStudentRelinkRequestResponse reject(UUID adminAuthUserId, UUID requestEntityId) {
        Admin admin = access.getAdmin(adminAuthUserId);
        StudentRelinkRequest request = findPending(requestEntityId);
        request.reject(admin);
        return response(request);
    }

    private StudentRelinkRequest findPending(UUID requestEntityId) {
        StudentRelinkRequest request = requests.findByEntityId(requestEntityId)
            .orElseThrow(StudentRelinkRequestNotFoundException::new);
        if (!request.isPending()) {
            throw new StudentRelinkConflictException("이미 처리된 재연결 요청입니다.");
        }
        return request;
    }

    private AdminStudentRelinkRequestResponse response(StudentRelinkRequest request) {
        Student student = request.getStudent();
        return new AdminStudentRelinkRequestResponse(
            request.getEntityId(),
            request.getEmail(),
            request.getStatus(),
            request.getCreatedAt(),
            request.getResolvedAt(),
            request.getResolvedByAdmin() == null ? null : AdminAccessService.identity(request.getResolvedByAdmin()),
            StudentService.toResponse(student, majors.findAllByStudentAndDeletedAtIsNull(student)),
            student.getCreatedAt()
        );
    }
}
