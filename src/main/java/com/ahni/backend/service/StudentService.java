package com.ahni.backend.service;

import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.StudentMajorUpdateRequest;
import com.ahni.backend.dto.StudentProfileRegistrationRequest;
import com.ahni.backend.dto.StudentProfileResponse;
import com.ahni.backend.entity.*;
import com.ahni.backend.exception.DepartmentNotFoundException;
import com.ahni.backend.exception.DuplicateMajorDepartmentException;
import com.ahni.backend.exception.InvalidEnrollmentStatusException;
import com.ahni.backend.exception.StudentAlreadyRegisteredException;
import com.ahni.backend.exception.StudentEmailAlreadyRegisteredException;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.repository.DepartmentRepository;
import com.ahni.backend.repository.StudentMajorRepository;
import com.ahni.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class StudentService {
    private final StudentRepository studentRepository;
    private final DepartmentRepository departmentRepository;
    private final StudentMajorRepository studentMajorRepository;

    public StudentService(StudentRepository studentRepository, DepartmentRepository departmentRepository, StudentMajorRepository studentMajorRepository) {
        this.studentRepository = studentRepository;
        this.departmentRepository = departmentRepository;
        this.studentMajorRepository = studentMajorRepository;
    }

    public StudentProfileResponse getProfile(UUID authUserId) {
        Student student = studentRepository.findByAuthUserId(authUserId)
            .orElseThrow(StudentNotFoundException::new);
        List<StudentMajor> majors = studentMajorRepository
            .findAllByStudentAndDeletedAtIsNull(student);

        return toResponse(student, majors);
    }

    @Transactional
    public StudentProfileResponse registerProfile(UUID authUserId, String email, StudentProfileRegistrationRequest request) {
        if (studentRepository.findByAuthUserId(authUserId).isPresent()) {
            throw new StudentAlreadyRegisteredException();
        }
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new StudentEmailAlreadyRegisteredException();
        }

        EnrollmentStatus enrollmentStatus = request.enrollmentStatus();

        if (enrollmentStatus != EnrollmentStatus.ENROLLED && enrollmentStatus != EnrollmentStatus.LEAVE) {
            throw new InvalidEnrollmentStatusException();
        }

        EnumMap<MajorType, Department> departments = resolveDepartments(
            request.primaryDepartmentEntityId(),
            request.doubleMajorDepartmentEntityId(),
            request.minorDepartmentEntityId()
        );
        String nickname = getNickname(request.nickname());
        Student student = new Student(
            authUserId,
            email,
            request.admissionYear(),
            enrollmentStatus,
            nickname
        );

        Student savedStudent = studentRepository.save(student);
        List<StudentMajor> majors = departments.entrySet().stream()
            .map(entry -> new StudentMajor(savedStudent, entry.getValue(), entry.getKey()))
            .toList();
        studentMajorRepository.saveAll(majors);

        return toResponse(savedStudent, majors);

    }

    @Transactional
    public StudentProfileResponse replaceMajors(
        UUID authUserId,
        StudentMajorUpdateRequest request
    ) {
        Student student = studentRepository.findByAuthUserId(authUserId)
            .orElseThrow(StudentNotFoundException::new);
        EnumMap<MajorType, Department> requested = resolveDepartments(
            request.primaryDepartmentEntityId(),
            request.doubleMajorDepartmentEntityId(),
            request.minorDepartmentEntityId()
        );
        List<StudentMajor> active = studentMajorRepository
            .findAllByStudentAndDeletedAtIsNull(student);
        List<StudentMajor> retained = new ArrayList<>();

        for (StudentMajor current : active) {
            Department requestedDepartment = requested.get(current.getMajorType());
            if (requestedDepartment != null && current.isAssignedTo(requestedDepartment)) {
                retained.add(current);
                requested.remove(current.getMajorType());
            } else {
                current.softDelete();
            }
        }

        studentMajorRepository.flush();

        List<StudentMajor> created = requested.entrySet().stream()
            .map(entry -> new StudentMajor(student, entry.getValue(), entry.getKey()))
            .toList();
        studentMajorRepository.saveAll(created);

        List<StudentMajor> result = new ArrayList<>(retained);
        result.addAll(created);
        return toResponse(student, result);
    }

    private EnumMap<MajorType, Department> resolveDepartments(
        UUID primaryId,
        UUID doubleMajorId,
        UUID minorId
    ) {
        validateDistinctDepartments(primaryId, doubleMajorId, minorId);

        EnumMap<MajorType, Department> departments = new EnumMap<>(MajorType.class);
        departments.put(MajorType.PRIMARY, findActiveDepartment(primaryId));
        if (doubleMajorId != null) {
            departments.put(MajorType.DOUBLE_MAJOR, findActiveDepartment(doubleMajorId));
        }
        if (minorId != null) {
            departments.put(MajorType.MINOR, findActiveDepartment(minorId));
        }
        return departments;
    }

    private static void validateDistinctDepartments(
        UUID primaryId,
        UUID doubleMajorId,
        UUID minorId
    ) {
        List<UUID> ids = Stream.of(primaryId, doubleMajorId, minorId)
            .filter(Objects::nonNull)
            .toList();
        if (ids.stream().distinct().count() != ids.size()) {
            throw new DuplicateMajorDepartmentException();
        }
    }

    private Department findActiveDepartment(UUID entityId) {
        return departmentRepository.findByEntityId(entityId)
            .filter(department -> department.getDeletedAt() == null)
            .orElseThrow(DepartmentNotFoundException::new);
    }

    static StudentProfileResponse toResponse(
        Student student,
        Collection<StudentMajor> majors
    ) {
        EnumMap<MajorType, DepartmentResponse> departments = new EnumMap<>(MajorType.class);
        majors.forEach(major -> departments.put(
            major.getMajorType(),
            new DepartmentResponse(
                major.getDepartment().getEntityId(),
                major.getDepartment().getName()
            )
        ));

        DepartmentResponse primary = Optional
            .ofNullable(departments.get(MajorType.PRIMARY))
            .orElseThrow(() -> new IllegalStateException("학생의 주전공을 찾을 수 없습니다."));

        return new StudentProfileResponse(
            student.getEntityId(),
            student.getEmail(),
            student.getNickname(),
            primary,
            departments.get(MajorType.DOUBLE_MAJOR),
            departments.get(MajorType.MINOR),
            student.getAdmissionYear(),
            student.getEnrollmentStatus(),
            student.getAccountStatus()
        );
    }

    private static String getNickname(String nickname) {
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }
}
