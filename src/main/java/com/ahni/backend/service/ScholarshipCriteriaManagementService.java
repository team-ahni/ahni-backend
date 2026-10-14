package com.ahni.backend.service;

import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.ScholarshipCriteriaCreateRequest;
import com.ahni.backend.dto.ScholarshipCriteriaResponse;
import com.ahni.backend.dto.ScholarshipCriteriaUpdateRequest;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.ScholarshipCriteria;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.exception.DepartmentNotFoundException;
import com.ahni.backend.exception.ScholarshipCriteriaAlreadyExistsException;
import com.ahni.backend.exception.ScholarshipCriteriaNotFoundException;
import com.ahni.backend.repository.AdminRepository;
import com.ahni.backend.repository.DepartmentRepository;
import com.ahni.backend.repository.ScholarshipCriteriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ScholarshipCriteriaManagementService {
    private final AdminRepository adminRepository;
    private final DepartmentRepository departmentRepository;
    private final ScholarshipCriteriaRepository scholarshipCriteriaRepository;

    public ScholarshipCriteriaManagementService(
        AdminRepository adminRepository,
        DepartmentRepository departmentRepository,
        ScholarshipCriteriaRepository scholarshipCriteriaRepository
    ) {
        this.adminRepository = adminRepository;
        this.departmentRepository = departmentRepository;
        this.scholarshipCriteriaRepository = scholarshipCriteriaRepository;
    }

    public List<ScholarshipCriteriaResponse> findAll(
        UUID authUserId,
        UUID departmentEntityId,
        Integer admissionYear
    ) {
        ensureAdmin(authUserId);
        return scholarshipCriteriaRepository.findAllForAdmin(departmentEntityId, admissionYear)
            .stream()
            .map(ScholarshipCriteriaManagementService::toResponse)
            .toList();
    }

    @Transactional
    public ScholarshipCriteriaResponse create(
        UUID authUserId,
        ScholarshipCriteriaCreateRequest request
    ) {
        ensureAdmin(authUserId);
        Department department = departmentRepository
            .findByEntityIdAndDeletedAtIsNull(request.departmentEntityId())
            .orElseThrow(DepartmentNotFoundException::new);
        ensureUniqueName(department, request.admissionYear(), request.name());
        ScholarshipCriteria criteria = scholarshipCriteriaRepository.save(new ScholarshipCriteria(
            department,
            request.admissionYear(),
            request.name(),
            request.minGpa(),
            request.minCredits(),
            request.maxWarnings()
        ));
        return toResponse(criteria);
    }

    @Transactional
    public ScholarshipCriteriaResponse update(
        UUID authUserId,
        UUID criteriaEntityId,
        ScholarshipCriteriaUpdateRequest request
    ) {
        ensureAdmin(authUserId);
        ScholarshipCriteria criteria = findCriteria(criteriaEntityId);
        if (!criteria.getName().equals(request.name().trim())) {
            ensureUniqueName(criteria.getDepartment(), criteria.getAdmissionYear(), request.name());
        }
        criteria.update(
            request.name(),
            request.minGpa(),
            request.minCredits(),
            request.maxWarnings()
        );
        return toResponse(criteria);
    }

    @Transactional
    public void delete(UUID authUserId, UUID criteriaEntityId) {
        ensureAdmin(authUserId);
        scholarshipCriteriaRepository.delete(findCriteria(criteriaEntityId));
    }

    private void ensureAdmin(UUID authUserId) {
        if (!adminRepository.existsByAuthUserIdAndDeletedAtIsNull(authUserId)) {
            throw new AdminAccessDeniedException();
        }
    }

    private ScholarshipCriteria findCriteria(UUID criteriaEntityId) {
        return scholarshipCriteriaRepository.findByEntityId(criteriaEntityId)
            .orElseThrow(ScholarshipCriteriaNotFoundException::new);
    }

    private void ensureUniqueName(Department department, int admissionYear, String name) {
        if (scholarshipCriteriaRepository.existsByDepartmentAndAdmissionYearAndName(
            department,
            admissionYear,
            name.trim()
        )) {
            throw new ScholarshipCriteriaAlreadyExistsException();
        }
    }

    private static ScholarshipCriteriaResponse toResponse(ScholarshipCriteria criteria) {
        Department department = criteria.getDepartment();
        return new ScholarshipCriteriaResponse(
            criteria.getEntityId(),
            new DepartmentResponse(department.getEntityId(), department.getName()),
            criteria.getAdmissionYear(),
            criteria.getName(),
            criteria.getMinGpa(),
            criteria.getMinCredits(),
            criteria.getMaxWarnings()
        );
    }
}
