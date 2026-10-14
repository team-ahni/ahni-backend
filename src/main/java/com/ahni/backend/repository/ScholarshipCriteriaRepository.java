package com.ahni.backend.repository;

import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.ScholarshipCriteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScholarshipCriteriaRepository extends JpaRepository<ScholarshipCriteria, Long> {
    List<ScholarshipCriteria> findAllByDepartmentAndAdmissionYearOrderByNameAsc(
        Department department,
        Integer admissionYear
    );

    Optional<ScholarshipCriteria> findByEntityId(UUID entityId);

    boolean existsByDepartmentAndAdmissionYearAndName(
        Department department,
        Integer admissionYear,
        String name
    );

    @Query("""
        select criteria
        from ScholarshipCriteria criteria
        join fetch criteria.department department
        where (:departmentEntityId is null or department.entityId = :departmentEntityId)
          and (:admissionYear is null or criteria.admissionYear = :admissionYear)
        order by department.name asc,
                 criteria.admissionYear desc,
                 criteria.name asc
        """)
    List<ScholarshipCriteria> findAllForAdmin(
        @Param("departmentEntityId") UUID departmentEntityId,
        @Param("admissionYear") Integer admissionYear
    );
}
